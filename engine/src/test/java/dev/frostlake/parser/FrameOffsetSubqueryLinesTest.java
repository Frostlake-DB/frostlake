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
 * A window frame's offset that is no literal, in a query nested in another, is reported with one line more where the
 * account's reading of the outer query takes up again (all live-verified): a derived table's closing parenthesis - or
 * its query's FROM, in a derived table inside another or past a later item - and an EXISTS's token after the second
 * line. A query used as a value is refused at its SELECT first, then read by where it stands: in a call's argument
 * to the token after the first one in its window call, compared in a WHERE to that token and the frame's ROWS, as an
 * IN's list to the OVER past the call, and opening with a name and an operator at that operator.
 */
public class FrameOffsetSubqueryLinesTest extends MeasuredCellsTestSupport {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ev78 (v INT)");
    }

    @Test
    public void anOffsetInANestedQuery() {
        assertCells(new String[][] {
            {"SELECT * FROM (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev78)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'v'. | syntax error "
                    + "line 1 at position 71 unexpected 'AND'. | syntax error line 1 at position 97 unexpected ')'."},
            {"SELECT * FROM (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev78 WHERE "
                + "v > 0)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'v'. | syntax error "
                    + "line 1 at position 71 unexpected 'AND'. | syntax error line 1 at position 109 unexpected ')'."},
            {"SELECT * FROM (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN 1 + 1 PRECEDING AND CURRENT ROW) FROM ev78)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected '+'. | syntax error "
                    + "line 1 at position 75 unexpected 'AND'. | syntax error line 1 at position 101 unexpected ')'."},
            {"SELECT * FROM (SELECT * FROM (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) "
                + "FROM ev78))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected 'v'. | syntax error "
                    + "line 1 at position 86 unexpected 'AND'. | syntax error line 1 at position 103 unexpected "
                    + "'FROM'."},
            {"SELECT UPPER((SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev78 LIMIT "
                + "1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 25 unexpected 'v'. | syntax error line 1 at position 26 unexpected "
                    + "')'."},
            {"SELECT COALESCE((SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev78 "
                + "LIMIT 1), 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 28 unexpected 'v'. | syntax error line 1 at position 29 unexpected "
                    + "')'."},
            {"SELECT * FROM ev78 WHERE v = (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) "
                + "FROM ev78 LIMIT 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 41 unexpected 'v'. | syntax error line 1 at position 61 unexpected "
                    + "'ROWS'."},
            {"SELECT * FROM ev78 WHERE v IN (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) "
                + "FROM ev78)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 45 unexpected 'OVER'."},
            {"SELECT (SELECT v + SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev78 LIMIT 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 17 unexpected '+'."},
            {"SELECT * FROM (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev78) d",
                "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'v'. | syntax error "
                    + "line 1 at position 71 unexpected 'AND'. | syntax error line 1 at position 97 unexpected ')'."},
            {"SELECT * FROM (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev78 GROUP "
                + "BY v)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'v'. | syntax error "
                    + "line 1 at position 71 unexpected 'AND'. | syntax error line 1 at position 108 unexpected ')'."},
            {"SELECT * FROM (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev78 ORDER "
                + "BY v)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'v'. | syntax error "
                    + "line 1 at position 71 unexpected 'AND'. | syntax error line 1 at position 108 unexpected ')'."},
            {"SELECT * FROM (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev78, ev78 "
                + "e2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'v'. | syntax error "
                    + "line 1 at position 71 unexpected 'AND'. | syntax error line 1 at position 106 unexpected ')'."},
            {"SELECT * FROM (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN CURRENT ROW AND v FOLLOWING) FROM ev78)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 75 unexpected 'v'. | syntax error "
                    + "line 1 at position 77 unexpected 'FOLLOWING'."},
            {"SELECT * FROM (SELECT SUM(v) OVER (ORDER BY v ROWS v PRECEDING) FROM ev78)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'v'. | syntax error "
                    + "line 1 at position 53 unexpected 'PRECEDING'."},
            {"SELECT * FROM (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) AS s FROM ev78 "
                + "WHERE v > 0 ORDER BY s)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'v'. | syntax error "
                    + "line 1 at position 71 unexpected 'AND'. | syntax error line 1 at position 125 unexpected ')'."},
            {"SELECT (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev78 LIMIT 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 18 unexpected '('."},
            {"SELECT ABS((SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev78 LIMIT 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 23 unexpected 'v'. | syntax error line 1 at position 24 unexpected "
                    + "')'."},
            {"SELECT UPPER((SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev78))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 25 unexpected 'v'. | syntax error line 1 at position 26 unexpected "
                    + "')'."},
            {"SELECT * FROM ev78 WHERE v > (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) "
                + "FROM ev78 LIMIT 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 41 unexpected 'v'. | syntax error line 1 at position 61 unexpected "
                    + "'ROWS'."},
            {"SELECT * FROM ev78 WHERE EXISTS (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT "
                + "ROW) FROM ev78)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 77 unexpected 'v'. | syntax error "
                    + "line 1 at position 89 unexpected 'AND'. | syntax error line 1 at position 93 unexpected "
                    + "'CURRENT'."},
            {"SELECT * FROM (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev78 e)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'v'. | syntax error "
                    + "line 1 at position 71 unexpected 'AND'."},
            {"SELECT * FROM (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev78 AS e "
                + "WHERE e.v > 0)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'v'. | syntax error "
                    + "line 1 at position 71 unexpected 'AND'. | syntax error line 1 at position 116 unexpected ')'."},
            {"SELECT * FROM (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW), v FROM ev78)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'v'. | syntax error "
                    + "line 1 at position 71 unexpected 'AND'. | syntax error line 1 at position 91 unexpected "
                    + "'FROM'."},
            {"SELECT * FROM (SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev78)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'v'. | syntax error "
                    + "line 1 at position 74 unexpected 'AND'. | syntax error line 1 at position 100 unexpected ')'."},
            {"SELECT * FROM ev78 WHERE v IN (SELECT v + SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT "
                + "ROW) FROM ev78)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 49 unexpected 'OVER'."},
            {"SELECT * FROM ev78 WHERE v IN (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) "
                + "FROM ev78 WHERE v > 0)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 45 unexpected 'OVER'."},
            {"SELECT COALESCE((SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev78), 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 28 unexpected 'v'. | syntax error line 1 at position 29 unexpected "
                    + "')'."},
            {"SELECT * FROM ev78 WHERE v = (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) "
                + "FROM ev78)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 41 unexpected 'v'. | syntax error line 1 at position 61 unexpected "
                    + "'ROWS'."},
        });
    }
}
