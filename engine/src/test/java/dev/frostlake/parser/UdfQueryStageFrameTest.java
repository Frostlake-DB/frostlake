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
 * A SQL UDF's query body, a table function's too, whose bare stage the syntax refuses is read in the expression's
 * frame: the query's keyword, the token after the first bracket, and a closing parenthesis for a stage that is no
 * whole item (live-verified cells).
 */
public class UdfQueryStageFrameTest extends MeasuredCellsTestSupport {

    @Test
    public void aBrokenStageIsReadInTheFrame() {
        engine.execute("CREATE TABLE t (a INT)");
        assertCells(new String[][] {
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 9 unexpected '@st'."},
            {"CREATE FUNCTION fs() RETURNS TABLE (s VARCHAR) AS $$SELECT UPPER(@st), (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected '@st'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT @st$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT 1 IN (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected '@st'. | "
                + "syntax error line 1 at position 18 unexpected ')'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT @st, (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected '@st'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT BUILD_STAGE_FILE_URL(@st/d, 'x')$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 29 unexpected '@st/d'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT a FROM t WHERE a = (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 28 unexpected '@st'. | "
                + "syntax error line 1 at position 32 unexpected ')'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$ SELECT (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 2 unexpected 'SELECT'. | syntax error line 1 at position 10 unexpected '@st'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$\nSELECT (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 2 at "
                + "position 0 unexpected 'SELECT'. | syntax error line 2 at position 8 unexpected '@st'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT\n(@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 2 at position 1 unexpected '@st'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT BUILD_STAGE_FILE_URL(@ st, 'x')$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 29 unexpected '@'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT (1), (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 9 unexpected '1'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$WITH c AS (SELECT 1) SELECT (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'WITH'. | syntax error line 1 at position 12 unexpected 'SELECT'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$(SELECT (@st))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 2 unexpected 'SELECT'. | syntax error line 1 at position 10 unexpected '@st'. | "
                + "syntax error line 1 at position 14 unexpected ')'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT (@st) FROM t$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 9 unexpected '@st'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT ((@st))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 9 unexpected '('."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT UPPER(@st/d)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected '@st/d'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT @st/d$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT (@st/d)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 9 unexpected '@st/d'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS 'SELECT (@st)'",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 9 unexpected '@st'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT CALL_IT(@st || 'x')$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 16 unexpected '@st'. | "
                + "syntax error line 1 at position 26 unexpected ')'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT 'a', (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected '@st'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$select (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'select'. | syntax error line 1 at position 9 unexpected '@st'."},
        });
    }

    @Test
    public void aStageInBracketsOrAClause() {
        engine.execute("CREATE TABLE t (a INT)");
        assertCells(new String[][] {
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT UPPER(@st || 'x')$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected '@st'. | "
                + "syntax error line 1 at position 24 unexpected ')'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT 1 + (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected '@st'. | "
                + "syntax error line 1 at position 17 unexpected ')'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT (@st || 'x')$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 9 unexpected '@st'. | "
                + "syntax error line 1 at position 19 unexpected ')'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT 1 FROM t WHERE (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 24 unexpected '@st'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT 1 FROM t WHERE a = (@st) OR TRUE$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 28 unexpected '@st'. | "
                + "syntax error line 1 at position 40 unexpected ')'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT (@st), 1$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 9 unexpected '@st'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT 1 FROM t WHERE a = 1 AND (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 34 unexpected '@st'. | "
                + "syntax error line 1 at position 38 unexpected ')'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT 1 x, (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected '@st'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT 1 FROM t GROUP BY (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 27 unexpected '@st'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT (@st) + 1$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 9 unexpected '@st'. | "
                + "syntax error line 1 at position 17 unexpected ')'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT 1 IN (1, @st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 17 unexpected '@st'. | "
                + "syntax error line 1 at position 21 unexpected ')'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT (1 + @st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 9 unexpected '1'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT CALL_IT(1, (@st))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 16 unexpected '1'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT 1 FROM t WHERE a IN (1, @st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 32 unexpected '@st'. | "
                + "syntax error line 1 at position 36 unexpected ')'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT 1 = (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected '@st'. | "
                + "syntax error line 1 at position 17 unexpected ')'."},
        });
    }

    @Test
    public void otherQueryBodies() {
        engine.execute("CREATE TABLE t (a INT)");
        assertCells(new String[][] {
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT 1, (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 12 unexpected '@st'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT UPPER('a'), (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected ''a''."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT a FROM t WHERE a IN (1, 2) AND (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 40 unexpected '@st'. | "
                + "syntax error line 1 at position 44 unexpected ')'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT 1 FROM (SELECT (@st))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 16 unexpected 'SELECT'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT ('a'), (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 9 unexpected ''a''."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT @st FROM (SELECT 1)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 18 unexpected 'SELECT'."},
            {"CREATE FUNCTION fs() RETURNS TABLE (s VARCHAR) AS $$SELECT (@st)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 9 unexpected '@st'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT 1 FROM t WHERE a = (@st) + 1$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 28 unexpected '@st'. | "
                + "syntax error line 1 at position 36 unexpected ')'."},
            {"CREATE FUNCTION fs() RETURNS VARCHAR AS $$SELECT (@st) x$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 1 unexpected 'SELECT'. | syntax error line 1 at position 9 unexpected '@st'."},
        });
    }
}
