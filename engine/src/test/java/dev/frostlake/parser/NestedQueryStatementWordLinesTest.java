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
 * A statement word the FROM list of a query nested in another faulty query reads after a comma is refused as live
 * reads it (live-verified): each faulty query around it, compared or listed by an IN in a clause or standing in a select
 * list, is refused at its SELECT, outermost first, then the innermost query's lines as a compared query's — after a
 * DESCRIBE, DESC or EXPLAIN its next word and the ')' of the query around the innermost one.
 */
public class NestedQueryStatementWordLinesTest extends MeasuredCellsTestSupport {

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
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a > (SELECT 1 FROM rt955, (CALL "
                + "p955())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 63 unexpected 'SELECT'. | syntax error line 1 at position 90"
                + " unexpected 'p955'. | syntax error line 1 at position 95 unexpected ')'. | syntax error line"
                + " 1 at position 97 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, (DESCRIBE TABLE rt955)) AND a > 0",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 63 unexpected 'TABLE'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a "
                + "IN (SELECT 1 FROM rt955, (DESCRIBE TABLE rt955))))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 96"
                + " unexpected 'SELECT'. | syntax error line 1 at position 127 unexpected 'TABLE'. | syntax "
                + "error line 1 at position 140 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, "
                + "(DESCRIBE TABLE rt955))) AND a > 0",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 95"
                + " unexpected 'TABLE'. | syntax error line 1 at position 108 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a = (SELECT 1 FROM rt955 WHERE a = (SELECT 1 FROM rt955, (DESCRIBE"
                + " TABLE rt955)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 62 unexpected 'SELECT'. | syntax error line 1 at position 93"
                + " unexpected 'TABLE'. | syntax error line 1 at position 106 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a = (SELECT 1 FROM rt955 WHERE a = (SELECT 1 FROM rt955, (UPDATE "
                + "rt955 SET a = 1)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 62 unexpected 'SELECT'. | syntax error line 1 at position 84"
                + " unexpected 'UPDATE'. | syntax error line 1 at position 107 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, (UPDATE"
                + " rt955 SET a = 1)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 86"
                + " unexpected 'UPDATE'. | syntax error line 1 at position 109 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, (CALL "
                + "p955(1))))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 91"
                + " unexpected 'p955'. | syntax error line 1 at position 96 unexpected '1'. | syntax error line"
                + " 1 at position 98 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT a FROM rt955, (CALL "
                + "p955())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 91"
                + " unexpected 'p955'. | syntax error line 1 at position 96 unexpected ')'. | syntax error line"
                + " 1 at position 98 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, "
                + "(EXPLAIN SELECT 1)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 94"
                + " unexpected 'SELECT'. | syntax error line 1 at position 104 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a = (SELECT 1 FROM rt955, (DESCRIBE TABLE rt955)) AND a > 0",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 62 unexpected 'TABLE'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, "
                + "(DESCRIBE TABLE rt955)) AND a > 0)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 95"
                + " unexpected 'TABLE'. | syntax error line 1 at position 118 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, (CALL "
                + "p955())) AND a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 91"
                + " unexpected 'p955'. | syntax error line 1 at position 96 unexpected ')'. | syntax error line"
                + " 1 at position 98 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, (CALL "
                + "p955()))) AND a = 1 2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 91"
                + " unexpected 'p955'. | syntax error line 1 at position 96 unexpected ')'. | syntax error line"
                + " 1 at position 98 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a = (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, "
                + "(DESCRIBE TABLE rt955)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 63 unexpected 'SELECT'. | syntax error line 1 at position 94"
                + " unexpected 'TABLE'. | syntax error line 1 at position 107 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE ABS(a) IN (SELECT 1 FROM rt955, "
                + "(CALL p955())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 69 unexpected 'SELECT'. | syntax error line 1 at position 96"
                + " unexpected 'p955'. | syntax error line 1 at position 101 unexpected ')'. | syntax error "
                + "line 1 at position 103 unexpected ')'."},
            {"SELECT (SELECT 1 FROM rt955 WHERE a = (SELECT 1 FROM rt955, (DESCRIBE TABLE rt955))) FROM "
                + "rt955",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 39 unexpected 'SELECT'. | syntax error line 1 at position 70"
                + " unexpected 'TABLE'. | syntax error line 1 at position 83 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a NOT IN (SELECT 1 FROM rt955 WHERE a NOT IN (SELECT 1 FROM rt955,"
                + " (CALL p955())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 72 unexpected 'SELECT'. | syntax error line 1 at position 99"
                + " unexpected 'p955'. | syntax error line 1 at position 104 unexpected ')'. | syntax error "
                + "line 1 at position 106 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a "
                + "IN (SELECT 1 FROM rt955, (DESCRIBE TABLE rt955))) AND a > 0)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 96"
                + " unexpected 'SELECT'. | syntax error line 1 at position 127 unexpected 'TABLE'. | syntax "
                + "error line 1 at position 140 unexpected ')'."},
        });
    }

    @Test
    public void comparedAndSelectList() {
        engine.execute("CREATE OR REPLACE TABLE rt955 (a INT)");
        assertCells(new String[][] {
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
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT a FROM rt955, (CALL p955()))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 59 unexpected 'p955'. | syntax error line 1 at position 64 "
                + "unexpected ')'. | syntax error line 1 at position 66 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a = (SELECT a FROM rt955, (CALL p955()))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 58 unexpected 'p955'. | syntax error line 1 at position 63 "
                + "unexpected ')'. | syntax error line 1 at position 65 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT a FROM rt955 WHERE a = (SELECT a FROM rt955, "
                + "(DESCRIBE TABLE rt955)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 63 unexpected 'SELECT'. | syntax error line 1 at position 94"
                + " unexpected 'TABLE'. | syntax error line 1 at position 107 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a = (SELECT 1 FROM rt955 WHERE a IN (SELECT a FROM rt955, (CALL "
                + "p955())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 63 unexpected 'SELECT'. | syntax error line 1 at position 90"
                + " unexpected 'p955'. | syntax error line 1 at position 95 unexpected ')'. | syntax error line"
                + " 1 at position 97 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, "
                + "(DESCRIBE TABLE rt955 USING t)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 95"
                + " unexpected 'TABLE'. | syntax error line 1 at position 116 unexpected ')'."},
        });
    }
}
