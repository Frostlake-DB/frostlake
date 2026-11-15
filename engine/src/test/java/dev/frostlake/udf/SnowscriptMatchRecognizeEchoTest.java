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
 * The echo of a MATCH_RECOGNIZE in the expression of a Snowscript UDF's body the account refuses: the clause over
 * SELECT * of its source with every default spelled out, under a query of its partition keys and measures - and
 * under ONE ROW PER MATCH a second one taking ANY_VALUE of each measure - its measures' row pattern calls marked ?,
 * its definitions under RUNNING IFNULL, a NEXT or a LAST in one a window over the clause's partitions and order
 * (all live-verified).
 */
public class SnowscriptMatchRecognizeEchoTest extends MeasuredCellsTestSupport {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rc_t (a INT, b INT, c INT)");
    }

    /** The clause's parts under ONE ROW PER MATCH: partitions, measures, rows per match, skips, patterns, an alias. */
    @Test
    public void oneRowPerMatchShapes() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER "
                + "MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) "
                + "values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER "
                + "MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) "
                + "values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST "
                + "LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (PARTITION BY b ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a "
                + "> 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT B AS "
                + "\"B\", ANY_VALUE(M) AS \"M\" FROM (SELECT B AS \"B\", ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM "
                + "RC_T AS RC_T MATCH_RECOGNIZE (PARTITION BY B ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() "
                + "AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING "
                + "IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m, FIRST(a) AS f PATTERN (x) DEFINE x AS a "
                + "> 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\", ANY_VALUE(F) AS \"F\" FROM (SELECT ?MATCH_NUMBER() AS \"M\", ?FIRST(A) AS "
                + "\"F\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES "
                + "?MATCH_NUMBER() AS M, ?FIRST(A) AS F ONE ROW PER MATCH AFTER MATCH SKIP PAST LAST ROW PATTERN (X) "
                + "DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m ALL ROWS PER MATCH PATTERN (x) DEFINE x "
                + "AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT *, "
                + "?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE (ORDER BY A ASC NULLS "
                + "LAST MEASURES ?MATCH_NUMBER() AS M ALL ROWS PER MATCH SHOW EMPTY MATCHES AFTER MATCH SKIP PAST "
                + "LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a > PREV(a)))); "
                + "END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER "
                + "MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, PREV(A))) = TRUE, "
                + "FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT mr.m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a > 0) AS mr)); "
                + "END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT MR.M AS \"MR.M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER "
                + "MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) "
                + "values) MR))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES CLASSIFIER() AS m PATTERN (x) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?CLASSIFIER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?CLASSIFIER() AS M ONE ROW PER MATCH AFTER "
                + "MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) "
                + "values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m AFTER MATCH SKIP TO NEXT ROW PATTERN (x) "
                + "DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER "
                + "MATCH SKIP TO NEXT ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) "
                + "values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a DESC MEASURES MATCH_NUMBER() AS m PATTERN (x y+) DEFINE x AS a > 0, y "
                + "AS b > 1))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A DESC NULLS FIRST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH "
                + "AFTER MATCH SKIP PAST LAST ROW PATTERN ((X Y+)) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, "
                + "FALSE), Y AS RUNNING IFNULL((\">\"(B, 1)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m ONE ROW PER MATCH PATTERN (x y) DEFINE x "
                + "AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER "
                + "MATCH SKIP PAST LAST ROW PATTERN ((X Y)) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, "
                + "FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES FINAL LAST(b) AS m PATTERN (x+) DEFINE x AS b > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT FINAL ?LAST(B) AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES FINAL ?LAST(B) AS M ONE ROW PER MATCH AFTER "
                + "MATCH SKIP PAST LAST ROW PATTERN (X+) DEFINE X AS RUNNING IFNULL((\">\"(B, 0)) = TRUE, FALSE))) "
                + "values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS b = 1 AND c < "
                + "2))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER "
                + "MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((AND(\"=\"(B, 1), \"<\"(C, 2))) = "
                + "TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT COUNT(*) FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS b > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT \"COUNT(*)\"() AS \"COUNT(*)\" "
                + "FROM (SELECT ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T "
                + "AS RC_T MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER "
                + "MATCH AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(B, 0)) = TRUE, "
                + "FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (PARTITION BY b, c ORDER BY a, c DESC MEASURES MATCH_NUMBER() AS m PATTERN (x) "
                + "DEFINE x AS b > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT B AS "
                + "\"B\", C AS \"C\", ANY_VALUE(M) AS \"M\" FROM (SELECT B AS \"B\", C AS \"C\", ?MATCH_NUMBER() AS "
                + "\"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE (PARTITION BY B, C ORDER BY A ASC NULLS "
                + "LAST, C DESC NULLS FIRST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST "
                + "LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(B, 0)) = TRUE, FALSE))) values) values))"},
        });
    }

    /** Measures of every kind, the forms of a pattern, and the definitions' operators and navigations. */
    @Test
    public void measuresPatternsAndDefinitions() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES b + 1 AS m PATTERN (x) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT \"+\"(B, 1) AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES \"+\"(B, 1) AS M ONE ROW PER MATCH AFTER "
                + "MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) "
                + "values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES SUM(b) AS m PATTERN (x+) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT SUM(B) AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES SUM(B) AS M ONE ROW PER MATCH AFTER MATCH "
                + "SKIP PAST LAST ROW PATTERN (X+) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) "
                + "values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES COUNT(*) AS m PATTERN (x+) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT \"COUNT(*)\"() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES \"COUNT(*)\"() AS M ONE ROW PER MATCH AFTER "
                + "MATCH SKIP PAST LAST ROW PATTERN (X+) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) "
                + "values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES RUNNING LAST(b) AS m PATTERN (x+) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT RUNNING ?LAST(B) AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES RUNNING ?LAST(B) AS M ONE ROW PER MATCH AFTER "
                + "MATCH SKIP PAST LAST ROW PATTERN (X+) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) "
                + "values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES x.b AS m PATTERN (x) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT X.B AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES X.B AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST LAST ROW "
                + "PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES LAST(x.b) AS m PATTERN (x+) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?LAST(X.B) AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?LAST(X.B) AS M ONE ROW PER MATCH AFTER MATCH "
                + "SKIP PAST LAST ROW PATTERN (X+) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) "
                + "values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES PREV(b) AS m PATTERN (x) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?PREV(B) AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?PREV(B) AS M ONE ROW PER MATCH AFTER MATCH "
                + "SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) "
                + "values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() + 1 AS m PATTERN (x) DEFINE x AS a > 0))); "
                + "END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT \"+\"(MATCH_NUMBER(), 1) AS \"M\" FROM SELECT * FROM RC_T AS "
                + "RC_T MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES \"+\"(MATCH_NUMBER(), 1) AS M ONE ROW "
                + "PER MATCH AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = "
                + "TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES b AS m PATTERN (x) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT B AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES B AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST LAST ROW "
                + "PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES AVG(b) AS m, MIN(c) AS n PATTERN (x+) DEFINE x AS a > 0))); "
                + "END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\", ANY_VALUE(N) AS \"N\" FROM (SELECT AVG(B) AS \"M\", MIN(C) AS \"N\" FROM "
                + "SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES AVG(B) AS M, "
                + "MIN(C) AS N ONE ROW PER MATCH AFTER MATCH SKIP PAST LAST ROW PATTERN (X+) DEFINE X AS RUNNING "
                + "IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x | y) DEFINE x AS a > 0, y AS "
                + "a < 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER "
                + "MATCH SKIP PAST LAST ROW PATTERN ((X | Y)) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, "
                + "FALSE), Y AS RUNNING IFNULL((\"<\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN ((x y)+) DEFINE x AS a > 0, y AS "
                + "a < 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER "
                + "MATCH SKIP PAST LAST ROW PATTERN ((X Y)+) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, "
                + "FALSE), Y AS RUNNING IFNULL((\"<\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (^ x $) DEFINE x AS a > 0))); "
                + "END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER "
                + "MATCH SKIP PAST LAST ROW PATTERN ((^(X)$)) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, "
                + "FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x {- y -} z) DEFINE x AS a > 0, "
                + "y AS a < 0, z AS a = 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER "
                + "MATCH SKIP PAST LAST ROW PATTERN ((X {-Y-} Z)) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, "
                + "FALSE), Y AS RUNNING IFNULL((\"<\"(A, 0)) = TRUE, FALSE), Z AS RUNNING IFNULL((\"=\"(A, 0)) = "
                + "TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (PERMUTE(x, y)) DEFINE x AS a > "
                + "0, y AS a < 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER "
                + "MATCH SKIP PAST LAST ROW PATTERN (PERMUTE(X, Y)) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, "
                + "FALSE), Y AS RUNNING IFNULL((\"<\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x{2} y{1,3} z*? w+?) DEFINE x "
                + "AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER "
                + "MATCH SKIP PAST LAST ROW PATTERN ((X{2,2} Y{1,3} Z*? W+?)) DEFINE X AS RUNNING IFNULL((\">\"(A, "
                + "0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a IS NULL OR NOT "
                + "b BETWEEN 1 AND 2))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER "
                + "MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((OR(\"IS NULL\"(A), "
                + "NOT(BETWEEN(B, 1, 2)))) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x+) DEFINE x AS FIRST(a) < "
                + "LAST(a)))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER "
                + "MATCH SKIP PAST LAST ROW PATTERN (X+) DEFINE X AS RUNNING IFNULL((\"<\"(FIRST(A), LAST_VALUE(A) "
                + "OVER (ORDER BY A ASC NULLS LAST))) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m ALL ROWS PER MATCH OMIT EMPTY MATCHES "
                + "PATTERN (x) DEFINE x AS a > 0) mr)); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT *, "
                + "?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE (ORDER BY A ASC NULLS "
                + "LAST MEASURES ?MATCH_NUMBER() AS M ALL ROWS PER MATCH OMIT EMPTY MATCHES AFTER MATCH SKIP PAST "
                + "LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) MR))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m ALL ROWS PER MATCH WITH UNMATCHED ROWS "
                + "PATTERN (x) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT *, "
                + "?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE (ORDER BY A ASC NULLS "
                + "LAST MEASURES ?MATCH_NUMBER() AS M ALL ROWS PER MATCH WITH UNMATCHED ROWS AFTER MATCH SKIP PAST "
                + "LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT m FROM rc_t "
                + "MATCH_RECOGNIZE (PARTITION BY b ORDER BY a MEASURES MATCH_NUMBER() AS m ALL ROWS PER MATCH "
                + "PATTERN (x) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT M AS \"M\" FROM (SELECT *, "
                + "?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE (PARTITION BY B ORDER BY "
                + "A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ALL ROWS PER MATCH SHOW EMPTY MATCHES AFTER MATCH "
                + "SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (PARTITION BY b ORDER BY a PATTERN (x) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT B AS \"B\" FROM "
                + "(SELECT B AS \"B\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE (PARTITION BY B ORDER BY A ASC "
                + "NULLS LAST ONE ROW PER MATCH AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING "
                + "IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST LAST ROW PATTERN (X) "
                + "DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m AFTER MATCH SKIP TO LAST x PATTERN (x y) "
                + "DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP TO "
                + "LAST X PATTERN ((X Y)) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m AFTER MATCH SKIP TO FIRST y PATTERN (x "
                + "y) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP TO "
                + "FIRST Y PATTERN ((X Y)) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t AS t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS T MATCH_RECOGNIZE (ORDER "
                + "BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST LAST "
                + "ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS \"Mx\" PATTERN (x) DEFINE x AS a > 0))); "
                + "END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(\"Mx\") "
                + "AS \"Mx\" FROM (SELECT ?MATCH_NUMBER() AS \"Mx\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS Mx ONE ROW PER MATCH AFTER MATCH SKIP PAST "
                + "LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM (SELECT * "
                + "FROM rc_t) MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a > "
                + "0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST "
                + "LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a > 0) JOIN rc_t "
                + "r2 ON TRUE)); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST "
                + "LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values "
                + "INNER JOIN RC_T AS R2 ON (TRUE)))"},
        });
    }

    /**
     * A derived source, anchors, count quantifiers, defaults spelled out, NEXT and LAST in a definition, and the
     * relation's name.
     */
    @Test
    public void sourcesAnchorsAndDefaults() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM (SELECT a, "
                + "b FROM rc_t) MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a > "
                + "0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT A AS \"A\", B AS \"B\" FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER "
                + "MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) "
                + "values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM (SELECT a, "
                + "b FROM rc_t) s MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a "
                + "> 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT A AS \"A\", B AS \"B\" FROM RC_T AS RC_T "
                + "MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER "
                + "MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) "
                + "values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y | z) DEFINE x AS a > 0, y "
                + "AS a < 0, z AS a = 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST "
                + "LAST ROW PATTERN (((X Y) | Z)) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE), Y AS "
                + "RUNNING IFNULL((\"<\"(A, 0)) = TRUE, FALSE), Z AS RUNNING IFNULL((\"=\"(A, 0)) = TRUE, FALSE))) "
                + "values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN ((x | y) z) DEFINE x AS a > 0, y "
                + "AS a < 0, z AS a = 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST "
                + "LAST ROW PATTERN (((X | Y) Z)) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE), Y AS "
                + "RUNNING IFNULL((\"<\"(A, 0)) = TRUE, FALSE), Z AS RUNNING IFNULL((\"=\"(A, 0)) = TRUE, FALSE))) "
                + "values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (^ x) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST "
                + "LAST ROW PATTERN ((^(X))) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) val"
                + "ues))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x $) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST "
                + "LAST ROW PATTERN (((X)$)) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) val"
                + "ues))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (^ x y) DEFINE x AS a > 0, y AS "
                + "a < 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST "
                + "LAST ROW PATTERN ((^(X Y))) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE), Y AS RUNNING "
                + "IFNULL((\"<\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x y $) DEFINE x AS a > 0, y AS "
                + "a < 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST "
                + "LAST ROW PATTERN (((X Y)$)) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE), Y AS RUNNING "
                + "IFNULL((\"<\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x{,3} y{2,}) DEFINE x AS a > 0, "
                + "y AS a < 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST "
                + "LAST ROW PATTERN ((X{0,3} Y{2,0})) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE), Y AS "
                + "RUNNING IFNULL((\"<\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN ((x)) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST "
                + "LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x?) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST "
                + "LAST ROW PATTERN (X?) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m AFTER MATCH SKIP TO y PATTERN (x y) "
                + "DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP TO "
                + "LAST Y PATTERN ((X Y)) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES NEXT(b) AS m PATTERN (x) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?NEXT(B) AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE (ORDER BY A "
                + "ASC NULLS LAST MEASURES ?NEXT(B) AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST LAST ROW PATTERN "
                + "(X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES FINAL COUNT(*) AS m PATTERN (x+) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT FINAL \"COUNT(*)\"() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES FINAL \"COUNT(*)\"() AS M ONE ROW PER MATCH AFTER MATCH SKIP "
                + "PAST LAST ROW PATTERN (X+) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) "
                + "values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES LAST(b, 1) AS m PATTERN (x+) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?LAST(B, 1) AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE (ORDER BY "
                + "A ASC NULLS LAST MEASURES ?LAST(B, 1) AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST LAST ROW "
                + "PATTERN (X+) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a > NEXT(a)))); "
                + "END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST "
                + "LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, LEAD(A) OVER (ORDER BY A ASC NULLS "
                + "LAST))) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a > PREV(a, "
                + "2)))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST "
                + "LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, PREV(A, 2))) = TRUE, FALSE))) values) "
                + "values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x+) DEFINE x AS LAST(a) > 1))); "
                + "END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST "
                + "LAST ROW PATTERN (X+) DEFINE X AS RUNNING IFNULL((\">\"(LAST_VALUE(A) OVER (ORDER BY A ASC NULLS "
                + "LAST), 1)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (PARTITION BY b ORDER BY a DESC, c MEASURES MATCH_NUMBER() AS m PATTERN (x+) "
                + "DEFINE x AS FIRST(a) < LAST(a)))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT B AS \"B\", "
                + "ANY_VALUE(M) AS \"M\" FROM (SELECT B AS \"B\", ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T "
                + "AS RC_T MATCH_RECOGNIZE (PARTITION BY B ORDER BY A DESC NULLS FIRST, C ASC NULLS LAST MEASURES "
                + "?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST LAST ROW PATTERN (X+) DEFINE X AS "
                + "RUNNING IFNULL((\"<\"(FIRST(A), LAST_VALUE(A) OVER (PARTITION BY B ORDER BY A DESC NULLS FIRST, C "
                + "ASC NULLS LAST))) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (PARTITION BY b + 1 ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x "
                + "AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT \"B + 1\" AS \"B + "
                + "1\", ANY_VALUE(M) AS \"M\" FROM (SELECT B + 1 AS \"B + 1\", ?MATCH_NUMBER() AS \"M\" FROM SELECT "
                + "* FROM RC_T AS RC_T MATCH_RECOGNIZE (PARTITION BY B + 1 ORDER BY A ASC NULLS LAST MEASURES "
                + "?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS "
                + "RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a ALL ROWS PER MATCH PATTERN (x) DEFINE x AS a > 0))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT * FROM SELECT * "
                + "FROM RC_T AS RC_T MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST ALL ROWS PER MATCH SHOW EMPTY "
                + "MATCHES AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = "
                + "TRUE, FALSE))) values))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a > 0) \"Mr\")); "
                + "END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST "
                + "LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) Mr))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT r.m, r2.b FROM "
                + "rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a > 0) r "
                + "JOIN rc_t r2 ON r.m = r2.b)); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT R.M AS \"R.M\", R2.B AS \"R2.B\" "
                + "FROM (SELECT ANY_VALUE(M) AS \"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T "
                + "AS RC_T MATCH_RECOGNIZE (ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER "
                + "MATCH AFTER MATCH SKIP PAST LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, "
                + "FALSE))) values) R INNER JOIN RC_T AS R2 ON (\"=\"(R.M, R2.B))))"},
            {"CREATE OR REPLACE FUNCTION emr(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() AS m PATTERN (x) DEFINE x AS a > 0) AS r "
                + "(n))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT ANY_VALUE(M) AS "
                + "\"M\" FROM (SELECT ?MATCH_NUMBER() AS \"M\" FROM SELECT * FROM RC_T AS RC_T MATCH_RECOGNIZE "
                + "(ORDER BY A ASC NULLS LAST MEASURES ?MATCH_NUMBER() AS M ONE ROW PER MATCH AFTER MATCH SKIP PAST "
                + "LAST ROW PATTERN (X) DEFINE X AS RUNNING IFNULL((\">\"(A, 0)) = TRUE, FALSE))) values) R))"},
        });
    }

    /** A row pattern quantifier's question mark is no bind in a view or a SQL function, where a bare one is. */
    @Test
    public void aPatternQuantifiersQuestionMarkIsNoBind() {
        engine.execute("INSERT INTO rc_t VALUES (1, 1, 1), (2, 2, 2)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE VIEW mrv AS SELECT * FROM rc_t MATCH_RECOGNIZE (ORDER BY a MEASURES MATCH_NUMBER() "
                + "AS m PATTERN (x? y*?) DEFINE x AS a > 0, y AS a > 1)", "View MRV successfully created."},
            {"SELECT COUNT(*) FROM mrv", "2"},
            {"CREATE OR REPLACE FUNCTION mrf() RETURNS INT AS 'SELECT COUNT(*) FROM rc_t MATCH_RECOGNIZE (ORDER BY a "
                + "MEASURES MATCH_NUMBER() AS m PATTERN (x?) DEFINE x AS a > 0)'",
                "Function MRF successfully created."},
            {"SELECT mrf()", "2"},
            {"CREATE OR REPLACE VIEW mrw AS SELECT ? AS q",
                "REFUSED SQL compilation error: error line 1 at position 37 | Bind variables not allowed in view and "
                    + "UDF definitions."},
        });
    }
}
