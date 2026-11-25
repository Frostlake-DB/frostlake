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

package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The columns a MATCH_RECOGNIZE definition may qualify (all live-verified): its own symbol's, read as the column
 * alone; any other qualifier refuses the statement as a correlated pattern definition, and any qualifier inside a NEXT
 * or a LAST as a window function with a pattern variable predicate, the first such name met deciding. A definition of
 * a symbol the pattern does not name is not read. The refusals are told when a view, a SQL function or a Snowscript
 * UDF is created; in a Snowscript UDF body the account refuses for any other reason the echo prints a quoted pattern
 * symbol upper-cased, only the definitions of the symbols the pattern names, and a qualified order key as written.
 */
public class MatchRecognizeDefinitionReferencesTest extends MeasuredCellsTestSupport {

    @Test
    public void snowscriptBodies() {
        engine.execute("CREATE OR REPLACE TABLE rc_t (a INT, b INT, c INT)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) DEFINE x AS a > "
                + "NEXT(a), y AS x.b < PREV(b, 2)))); END $$",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) DEFINE x AS a > "
                + "0, y AS x.b < 1))); END $$",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS x.a > "
                + "0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, "
                + "FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t t MATCH_RECOGNIZE (ORDER BY t.a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS "
                + "t.a > 0) r)); END $$",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS t.a "
                + "> 0) r)); END $$",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t t MATCH_RECOGNIZE (ORDER BY t.a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a "
                + "> 0) r)); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS T "
                + "MATCH_RECOGNIZE (ORDER BY T.A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH"
                + " AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE,"
                + " FALSE))) values) R))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS rc_t.a"
                + " > 0))); END $$",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) DEFINE x AS y.a "
                + "> 0, y AS a > 1))); END $$",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS "
                + "PREV(x.a) > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(PREV(A), 0)) = "
                + "TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES x.a AS m PATTERN (x) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT X.A AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES X.A AS M ONE ROW PER MATCH AFTER MATCH "
                + "SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) "
                + "values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS zz.a >"
                + " 0))); END $$",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (PARTITION BY b ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) "
                + "DEFINE x AS rc_t.b > 0))); END $$",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "(SELECT * FROM rc_t) s MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) "
                + "DEFINE x AS s.a > 0))); END $$",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES SUM(b) AS m PATTERN (x+) DEFINE x AS a > 0))); END"
                + " $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT SUM(B) AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES SUM(B) AS M ONE ROW PER MATCH AFTER "
                + "MATCH SKIP PAST LAST ROW PATTERN (X+) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, "
                + "FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x+) DEFINE x AS "
                + "ABS(b) > 2))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (X+) DEFINE X AS RUNNING IFNULL((\">\"(ABS(B), 2)) = "
                + "TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a ALL ROWS PER MATCH PATTERN (x) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM (SELECT * "
                + "FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST ALL ROWS PER "
                + "MATCH SHOW EMPTY MATCHES AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING "
                + "IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT b FROM "
                + "rc_t MATCH_RECOGNIZE (PARTITION BY b ORDER BY a PATTERN (x) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT B AS \"B\" FROM (SELECT B "
                + "AS \"B\" FROM (SELECT B AS \"B\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE (PARTITION "
                + "BY B ORDER BY A ASC NULLS LAST ONE ROW PER MATCH AFTER MATCH SKIP PAST LAST ROW PATTERN (X) "
                + "DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (\"x\") DEFINE \"x\" "
                + "AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (X))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (\"X\") DEFINE \"X\" "
                + "AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, "
                + "FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE \"X\" AS a "
                + "> 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, "
                + "FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (\"x\") DEFINE x AS a "
                + "> 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, "
                + "FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) DEFINE x AS a > "
                + "0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN ((X Y)) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = "
                + "TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a > 0,"
                + " z AS b > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, "
                + "FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (\"x\" y) DEFINE \"x\""
                + " AS a > 0, y AS b > 1))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN ((X Y)) DEFINE Y AS RUNNING IFNULL((\">\"(B, 1)) = "
                + "TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (\"Ab\") DEFINE \"Ab\""
                + " AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (AB))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE \"x\" AS a "
                + "> 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (X))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE y AS a > "
                + "0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (X))) values) values))"},
            {"SELECT m FROM rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) "
                + "DEFINE x AS a > 0, y AS x.b < 1)",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"SELECT m FROM rc_t t MATCH_RECOGNIZE (ORDER BY t.a MEASURES MATCH_NUMBER() AS m PATTERN (x) "
                + "DEFINE x AS t.a > 0) r",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"SELECT m FROM rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x+) "
                + "DEFINE x AS SUM(b) > 2)",
                "REFUSED SQL compilation error: error line 1 at position 101 | This function requires an "
                + "explicit window frame definition (OVER clause without PARTITION BY and ORDER BY)."},
            {"SELECT m FROM rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (\"x\") "
                + "DEFINE \"x\" AS a > 0)",
                ""},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS X.a > "
                + "0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, "
                + "FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS "
                + "\"X\".a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, "
                + "FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS "
                + "\"x\".a > 0))); END $$",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (\"X\") DEFINE \"X\" "
                + "AS \"X\".a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, "
                + "FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) DEFINE x AS a > "
                + "0, y AS y.b > x.a))); END $$",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a > 0,"
                + " z AS y.b > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, "
                + "FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) DEFINE x AS "
                + "NEXT(y.a) > 0))); END $$",
                "REFUSED Unsupported feature 'Window functions with pattern variable predicate in DEFINE'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) DEFINE y AS b > "
                + "1, x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN ((X Y)) DEFINE Y AS RUNNING IFNULL((\">\"(B, 1)) = "
                + "TRUE, FALSE), X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS x.a > "
                + "NEXT(x.a)))); END $$",
                "REFUSED Unsupported feature 'Window functions with pattern variable predicate in DEFINE'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS x.a > "
                + "LAST(x.a)))); END $$",
                "REFUSED Unsupported feature 'Window functions with pattern variable predicate in DEFINE'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (\"a b\") DEFINE \"a "
                + "b\" AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (A B))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m AFTER MATCH SKIP TO x PATTERN "
                + "(\"x\" y) DEFINE y AS b > 1))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP TO LAST X PATTERN ((X Y)) DEFINE Y AS RUNNING IFNULL((\">\"(B, 1)) = TRUE, "
                + "FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS x.a > "
                + "0, z AS b > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, "
                + "FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a.a > "
                + "0))); END $$",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY rc_t.a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a"
                + " > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY RC_T.A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER "
                + "MATCH AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = "
                + "TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t t MATCH_RECOGNIZE (PARTITION BY t.b ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x)"
                + " DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "\"T.B\" AS \"T.B\", ANY_VALUE(M) AS \"M\" FROM (SELECT T.B AS \"T.B\", ?MATCH_NUMBER() AS "
                + "\"M\" FROM SELECT * FROM RC_T AS T MATCH_RECOGNIZE (PARTITION BY T.B ORDER BY A ASC NULLS "
                + "LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST LAST ROW PATTERN "
                + "(X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t t MATCH_RECOGNIZE (ORDER BY t.a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS "
                + "NEXT(a) > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS T "
                + "MATCH_RECOGNIZE (ORDER BY T.A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH"
                + " AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(LEAD(A) OVER "
                + "(ORDER BY T.A ASC NULLS LAST), 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS "
                + "(SELECT 1) > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"((SELECT 1 AS "
                + "\"1\" FROM (VALUES (null)) DUAL), 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (\"x\" | y) DEFINE y "
                + "AS b > 1))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN ((X | Y)) DEFINE Y AS RUNNING IFNULL((\">\"(B, 1)) = "
                + "TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS x.a > "
                + "0 AND \"x\".b > 0))); END $$",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (\"Ab\") DEFINE AB AS "
                + "a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (AB) DEFINE AB AS RUNNING IFNULL((\">\"(A, 0)) = "
                + "TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN (SELECT COUNT(*) FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) DEFINE x AS a > "
                + "0, y AS x.b < 1)); END $$",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN (SELECT COUNT(*) FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS x.a > "
                + "0)); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: (SELECT \"COUNT(*)\"() AS \"COUNT(*)\" "
                + "FROM (SELECT ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM "
                + "RC_T AS RC_T MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE "
                + "ROW PER MATCH AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING "
                + "IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values)"},
            {"SELECT m FROM rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) "
                + "DEFINE x AS x.a > 0)",
                ""},
            {"SELECT m FROM rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) "
                + "DEFINE x AS a > 0, z AS y.b > 0)",
                ""},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS "
                + "NEXT(rc_t.a) > 0))); END $$",
                "REFUSED Unsupported feature 'Window functions with pattern variable predicate in DEFINE'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS "
                + "NEXT(zz.a) > 0))); END $$",
                "REFUSED Unsupported feature 'Window functions with pattern variable predicate in DEFINE'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) DEFINE x AS y.b "
                + "> NEXT(x.a)))); END $$",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) DEFINE x AS "
                + "NEXT(x.a) > y.b))); END $$",
                "REFUSED Unsupported feature 'Window functions with pattern variable predicate in DEFINE'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) DEFINE x AS y.b "
                + "> 0, y AS NEXT(y.a) > 0))); END $$",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) DEFINE x AS "
                + "NEXT(x.a) > 0, y AS x.b > 0))); END $$",
                "REFUSED Unsupported feature 'Window functions with pattern variable predicate in DEFINE'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS "
                + "NEXT(a) > PREV(x.a)))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(LEAD(A) OVER "
                + "(ORDER BY A ASC NULLS LAST), PREV(A))) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS NEXT(a"
                + " + x.b) > 0))); END $$",
                "REFUSED Unsupported feature 'Window functions with pattern variable predicate in DEFINE'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS "
                + "FIRST(x.a) > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(FIRST(A), 0)) ="
                + " TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS "
                + "PREV(rc_t.a) > 0))); END $$",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS "
                + "ABS(x.a) > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(ABS(A), 0)) = "
                + "TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS "
                + "LAST(a) > 0, z AS NEXT(z.a) > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(LAST_VALUE(A) "
                + "OVER (ORDER BY A ASC NULLS LAST), 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (PARTITION BY b ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) "
                + "DEFINE x AS NEXT(x.a) > 0))); END $$",
                "REFUSED Unsupported feature 'Window functions with pattern variable predicate in DEFINE'."},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a > 0 "
                + "AND x.c > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((AND(\">\"(A, 0), "
                + "\">\"(C, 0))) = TRUE, FALSE))) values) values))"},
            {"SELECT m FROM rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) "
                + "DEFINE x AS PREV(x.a) > 0)",
                "REFUSED SQL compilation error: | Unknown function PREV."},
            {"SELECT m FROM rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) "
                + "DEFINE x AS NEXT(x.a) > 0)",
                "REFUSED Unsupported feature 'Window functions with pattern variable predicate in DEFINE'."},
            {"SELECT m FROM rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) "
                + "DEFINE x AS \"X\".a > 0)",
                ""},
            {"SELECT m FROM rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) "
                + "DEFINE x AS \"x\".a > 0)",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"SELECT m FROM rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) "
                + "DEFINE x AS a > 0, z AS b > 0)",
                ""},
            {"SELECT m FROM rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) "
                + "DEFINE x AS NEXT(y.a) > 0)",
                "REFUSED Unsupported feature 'Window functions with pattern variable predicate in DEFINE'."},
            {"SELECT m FROM rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) "
                + "DEFINE x AS x.a > 0 AND rc_t.b > 0)",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"SELECT m FROM rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) "
                + "DEFINE x AS zz.a > 0)",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
        });
    }

    @Test
    public void definitionsElsewhere() {
        engine.execute("CREATE OR REPLACE TABLE rc_t (a INT, b INT, c INT)");
        engine.execute("INSERT INTO rc_t VALUES (1, 2, 3), (2, 0, 1), (3, 5, 0), (0, 1, 1)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE VIEW emv AS SELECT m FROM rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES "
                + "MATCH_NUMBER() AS m PATTERN (x y) DEFINE x AS a > 0, y AS x.b < 1)",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"CREATE OR REPLACE VIEW emv AS SELECT m FROM rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES "
                + "MATCH_NUMBER() AS m PATTERN (x y) DEFINE x AS NEXT(x.a) > 0)",
                "REFUSED Unsupported feature 'Window functions with pattern variable predicate in DEFINE'."},
            {"CREATE OR REPLACE VIEW emv AS SELECT m FROM nosuch_t MATCH_RECOGNIZE (ORDER BY a MEASURES "
                + "MATCH_NUMBER() AS m PATTERN (x y) DEFINE x AS a > 0, y AS x.b < 1)",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"CREATE OR REPLACE VIEW emv AS SELECT * FROM rc_t WHERE a IN (SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) DEFINE x AS rc_t.a > "
                + "0))",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"CREATE OR REPLACE FUNCTION emr2() RETURNS TABLE (m INT) AS 'SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) DEFINE x AS a > 0, y "
                + "AS x.b < 1)'",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"CREATE OR REPLACE FUNCTION emr2() RETURNS INT AS 'SELECT MAX(m) FROM nosuch_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) DEFINE x AS a > 0, y "
                + "AS x.b < 1)'",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"CREATE OR REPLACE FUNCTION emr2() RETURNS INT AS 'SELECT MAX(m) FROM rc_t MATCH_RECOGNIZE "
                + "(ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) DEFINE x AS NEXT(zz.a) > 0)'",
                "REFUSED Unsupported feature 'Window functions with pattern variable predicate in DEFINE'."},
            {"CREATE OR REPLACE FUNCTION emr2() RETURNS INT AS 'SELECT MAX(nosuch_col) FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) DEFINE x AS a > 0, y "
                + "AS x.b < 1)'",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"SELECT m FROM rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) "
                + "DEFINE x AS x.a > 0, y AS y.b > 0)",
                "1"},
            {"SELECT m FROM rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) "
                + "DEFINE x AS NEXT(x.a) > 0)",
                "REFUSED Unsupported feature 'Window functions with pattern variable predicate in DEFINE'."},
            {"SELECT m FROM rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) "
                + "DEFINE x AS LAST(y.a) > 0)",
                "REFUSED Unsupported feature 'Window functions with pattern variable predicate in DEFINE'."},
            {"SELECT m FROM rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) "
                + "DEFINE x AS a > 0, z AS q.b < 1)",
                "1"},
            {"SELECT m FROM rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y) "
                + "DEFINE x AS \"x\".a > 0)",
                "REFUSED Unsupported feature 'Correlated pattern definitions'."},
            {"SELECT m, a FROM rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m, LAST(a) AS a"
                + " PATTERN (x y+) DEFINE x AS x.a > 0, y AS y.b > 0) ORDER BY m",
                "1|3"},
            {"SELECT m, n FROM rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m, COUNT(*) AS "
                + "n PATTERN (x+) DEFINE x AS \"X\".b >= 1, z AS a > 100) ORDER BY m",
                "1|2 ;; 2|1"},
        });
    }
}
