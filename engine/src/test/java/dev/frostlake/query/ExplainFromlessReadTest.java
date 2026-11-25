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

import org.junit.jupiter.api.Test;
/**
 * What EXPLAIN plans a SELECT with no FROM reading, as the account compiles it (live-verified). A select whose items
 * or WHERE call a function answering anew each time — the random family whatever its arguments, a row-place
 * generator, a SQL function whose body calls one, a procedure's token — or that aggregates, groups or computes a
 * window reads an empty ValuesClause; any other reads a Generator of one row. A WHERE condition computed from
 * constants is folded: a true one is dropped, a false or null one leaves a Generator of no rows; the others stay as a
 * Filter, an OR keeping its one side that answers anew beside false ones. A LIMIT over the ValuesClause is kept. An
 * Aggregate groups by no constant key, and a comparison or an IS NULL holds a call written with no arguments
 * without parentheses.
 */
public class ExplainFromlessReadTest extends MeasuredCellsTestSupport {

    @Test
    public void aFromlessSelectCallingAFreshFunctionReadsAValuesClause() {
        engine.execute("CREATE OR REPLACE PROCEDURE pr07(x NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN "
                + "RETURN x + 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fnr07(x NUMBER) RETURNS NUMBER AS 'x + UNIFORM(1, 10, "
                + "RANDOM())'");
        engine.execute("CREATE OR REPLACE FUNCTION fn07(x NUMBER) RETURNS NUMBER AS 'x + 1'");
        engine.execute("CREATE OR REPLACE TABLE e07 (n INT)");
        engine.execute("INSERT INTO e07 VALUES (1), (2)");
        engine.execute("SET atok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr07(NUMBER)', 'SESSION', "
                + "'USAGE'))");
        engine.execute("SET v07 = 5");
        assertAnswer("GlobalStats|null ;; Result|RANDOM() ;; ValuesClause|()",
            "EXPLAIN SELECT RANDOM() ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|UUID_STRING() ;; ValuesClause|()",
            "EXPLAIN SELECT UUID_STRING() ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 + (RANDOM()) ;; ValuesClause|()",
            "EXPLAIN SELECT 1 + RANDOM() ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|4 + (UNIFORM(1, 10, RANDOM())) ;; ValuesClause|()",
            "EXPLAIN SELECT fnr07(4) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|RANDOM(), 1 ;; ValuesClause|()",
            "EXPLAIN SELECT RANDOM() AS r, 1 AS s ->> SELECT \"operation\", \"expressions\" FROM "
                + "$1");
        assertAnswer("GlobalStats|null ;; Result|UNIFORM(1, 10, RANDOM()) ;; ValuesClause|()",
            "EXPLAIN SELECT UNIFORM(1, 10, RANDOM()) ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|UNIFORM(1, 10, 5) ;; ValuesClause|()",
            "EXPLAIN SELECT UNIFORM(1, 10, 5) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|RANDOM(42) ;; ValuesClause|()",
            "EXPLAIN SELECT RANDOM(42) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|RANDSTR(5, RANDOM()) ;; ValuesClause|()",
            "EXPLAIN SELECT RANDSTR(5, RANDOM()) ->> SELECT \"operation\", \"expressions\" FROM "
                + "$1");
        assertAnswer("GlobalStats|null ;; Result|SEQ4() ;; ValuesClause|()",
            "EXPLAIN SELECT SEQ4() ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|UUID_STRING('fe971b24-9572-4005-b22f-351e9c09274d', "
                + "'foo') ;; ValuesClause|()",
            "EXPLAIN SELECT UUID_STRING('fe971b24-9572-4005-b22f-351e9c09274d', 'foo') ->> SELECT"
                + " \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|ABS(RANDOM()) ;; ValuesClause|()",
            "EXPLAIN SELECT ABS(RANDOM()) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|5 ;; Generator|1",
            "EXPLAIN SELECT fn07(4) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|RANDOM() ;; TableScan|null",
            "EXPLAIN SELECT RANDOM() FROM e07 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|SEQ8() ;; ValuesClause|()",
            "EXPLAIN SELECT SEQ8() ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|1",
            "EXPLAIN SELECT 1 WHERE 1 = 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|0",
            "EXPLAIN SELECT 1 WHERE 1 = 2 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|RANDOM() > 0 ;; ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE RANDOM() > 0 ->> SELECT \"operation\", \"expressions\" FROM "
                + "$1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|1",
            "EXPLAIN SELECT 1 WHERE $v07 = 5 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|(4 + (UNIFORM(1, 10, RANDOM()))) > 5 ;; "
                + "ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE fnr07(4) > 5 ->> SELECT \"operation\", \"expressions\" FROM "
                + "$1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|1",
            "EXPLAIN SELECT 1 WHERE fn07(4) = 5 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|PR07(X) IS NOT NULL ;; ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE IDENTIFIER($atok)(4) IS NOT NULL ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|PR07(X) ;; Filter|RANDOM() > 0 ;; ValuesClause|()",
            "EXPLAIN SELECT IDENTIFIER($atok)(4) WHERE RANDOM() > 0 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|RANDOM() ;; ValuesClause|()",
            "EXPLAIN SELECT RANDOM() WHERE 1 = 1 ->> SELECT \"operation\", \"expressions\" FROM "
                + "$1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|UUID_STRING() <> 'x' ;; ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE UUID_STRING() <> 'x' ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("1",
            "SELECT 1 WHERE RANDOM() > 0 OR TRUE");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|RANDOM() <> 0 ;; ValuesClause|()",
            "EXPLAIN SELECT 1 AS a WHERE 2 > 1 AND RANDOM() <> 0 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|1",
            "EXPLAIN SELECT 1 WHERE CURRENT_DATE() > '2000-01-01' ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
    }

    @Test
    public void freshCallsAndConstantConditionsFold() {
        engine.execute("CREATE OR REPLACE PROCEDURE pr07(x NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN "
                + "RETURN x + 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fnq07() RETURNS NUMBER AS 'SELECT RANDOM()'");
        engine.execute("CREATE OR REPLACE TABLE e07 (n INT)");
        engine.execute("CREATE OR REPLACE SEQUENCE sq07");
        engine.execute("SET atok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr07(NUMBER)', 'SESSION', "
                + "'USAGE'))");
        engine.execute("SET v07 = 5");
        assertAnswer("GlobalStats|null ;; Result|SEQ1() ;; ValuesClause|()",
            "EXPLAIN SELECT SEQ1() ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|SEQ2() ;; ValuesClause|()",
            "EXPLAIN SELECT SEQ2() ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|RANDSTR(5, 5) ;; ValuesClause|()",
            "EXPLAIN SELECT RANDSTR(5, 5) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|CASE_FLATTENED(RANDOM() > 0, 1, 2) ;; ValuesClause|()",
            "EXPLAIN SELECT CASE WHEN RANDOM() > 0 THEN 1 ELSE 2 END ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|UNION_ALL(RANDOM(), 1) ;; UnionAll|null ;; "
                + "ValuesClause|() ;; Generator|1",
            "EXPLAIN SELECT RANDOM() UNION ALL SELECT 1 ->> SELECT \"operation\", \"expressions\""
                + " FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|1",
            "EXPLAIN SELECT 1 ORDER BY RANDOM() ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|COUNT(*) ;; Aggregate|aggExprs: [COUNT(*)] ;; "
                + "Filter|RANDOM() > 0 ;; ValuesClause|()",
            "EXPLAIN SELECT COUNT(*) WHERE RANDOM() > 0 ->> SELECT \"operation\", \"expressions\""
                + " FROM $1");
        assertAnswer("GlobalStats|null ;; Result|SUM(RANDOM()) ;; Aggregate|aggExprs: [SUM(RANDOM())] ;; "
                + "ValuesClause|()",
            "EXPLAIN SELECT SUM(RANDOM()) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|HASH(RANDOM()) ;; ValuesClause|()",
            "EXPLAIN SELECT HASH(RANDOM()) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|UUID_STRING() IS NULL ;; ValuesClause|()",
            "EXPLAIN SELECT UUID_STRING() IS NULL ->> SELECT \"operation\", \"expressions\" FROM "
                + "$1");
        assertAnswer("GlobalStats|null ;; Result|RANDOM() ;; Generator|0",
            "EXPLAIN SELECT RANDOM() WHERE 1 = 2 ->> SELECT \"operation\", \"expressions\" FROM "
                + "$1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|1",
            "EXPLAIN SELECT 1 WHERE RANDOM() > 0 OR 1 = 1 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|RANDOM() > 0 ;; ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE RANDOM() > 0 OR 1 = 2 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|0",
            "EXPLAIN SELECT 1 WHERE 1 = 2 AND RANDOM() > 0 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|0",
            "EXPLAIN SELECT 1 WHERE NULL ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|0",
            "EXPLAIN SELECT 1 WHERE $v07 = 6 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|0",
            "EXPLAIN SELECT 1 WHERE CURRENT_DATE() < '2000-01-01' ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|1",
            "EXPLAIN SELECT 1 WHERE 1 IN (1, 2) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|1",
            "EXPLAIN SELECT 1 WHERE 'a' LIKE 'a%' ->> SELECT \"operation\", \"expressions\" FROM "
                + "$1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|1",
            "EXPLAIN SELECT 1 WHERE TRUE ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|0",
            "EXPLAIN SELECT 1 WHERE FALSE ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|1",
            "EXPLAIN SELECT 1 WHERE CURRENT_USER() IS NOT NULL ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|0",
            "EXPLAIN SELECT 1 WHERE IDENTIFIER($atok)(4) = 5 AND 1 = 2 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|(RANDOM() > 0) AND (UUID_STRING() <> 'x') ;; "
                + "ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE RANDOM() > 0 AND UUID_STRING() <> 'x' ->> SELECT "
                + "\"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|NOT(RANDOM() > 0) ;; ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE NOT (RANDOM() > 0) ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|(RANDOM() >= 0) AND (RANDOM() <= 1) ;; "
                + "ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE RANDOM() BETWEEN 0 AND 1 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|1",
            "EXPLAIN SELECT 1 WHERE 2 > 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|1",
            "EXPLAIN SELECT 1 WHERE (SELECT 1) = 1 ->> SELECT \"operation\", \"expressions\" FROM"
                + " $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|(1 = 1) AND (RANDOM() > 0) ;; ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE RANDOM() > 0 HAVING 1 = 1 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|1",
            "EXPLAIN SELECT 1 WHERE 1 = 1 OR 1 = 2 ->> SELECT \"operation\", \"expressions\" FROM"
                + " $1");
    }

    @Test
    public void aggregatesWindowsAndLimitsOverTheOneRow() {
        engine.execute("CREATE OR REPLACE TABLE e07 (n INT)");
        assertAnswer("GlobalStats|null ;; Result|COUNT(*) ;; Aggregate|aggExprs: [COUNT(*)] ;; "
                + "ValuesClause|()",
            "EXPLAIN SELECT COUNT(*) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|SUM(1) ;; Aggregate|aggExprs: [SUM(1)] ;; ValuesClause|()",
            "EXPLAIN SELECT SUM(1) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Aggregate|null ;; Generator|1",
            "EXPLAIN SELECT DISTINCT 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Aggregate|null ;; ValuesClause|()",
            "EXPLAIN SELECT 1 GROUP BY 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|ROW_NUMBER() OVER (ORDER BY 1 ASC NULLS LAST) ;; "
                + "WindowFunction|ROW_NUMBER() OVER (ORDER BY 1 ASC NULLS LAST) ;; ValuesClause|()",
            "EXPLAIN SELECT ROW_NUMBER() OVER (ORDER BY 1) ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : SQL "
                + "compilation error: error line 1 at position 17 | found QUALIFY clause but no window "
                + "function.",
            "EXPLAIN SELECT 1 QUALIFY 1 = 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|1 = 1 ;; ValuesClause|()",
            "EXPLAIN SELECT 1 HAVING 1 = 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|0",
            "EXPLAIN SELECT 1 HAVING 1 = 2 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|COUNT(*) ;; Aggregate|aggExprs: [COUNT(*)] ;; Generator|0",
            "EXPLAIN SELECT COUNT(*) WHERE 1 = 2 ->> SELECT \"operation\", \"expressions\" FROM "
                + "$1");
        assertAnswer("GlobalStats|null ;; Result|RANDOM() ;; Aggregate|groupKeys: [RANDOM()] ;; "
                + "ValuesClause|()",
            "EXPLAIN SELECT DISTINCT RANDOM() ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|RANDOM() ;; Aggregate|groupKeys: [RANDOM()] ;; "
                + "ValuesClause|()",
            "EXPLAIN SELECT RANDOM() GROUP BY 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|ROW_NUMBER() OVER (ORDER BY RANDOM() ASC NULLS LAST) ;; "
                + "WindowFunction|ROW_NUMBER() OVER (ORDER BY RANDOM() ASC NULLS LAST) ;; "
                + "ValuesClause|()",
            "EXPLAIN SELECT ROW_NUMBER() OVER (ORDER BY RANDOM()) ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|SEQ4() > 0 ;; ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE SEQ4() > 0 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|(UNIFORM(1, 10, RANDOM())) > 5 ;; "
                + "ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE UNIFORM(1, 10, RANDOM()) > 5 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|((RANDOM()) + 1) > 5 ;; ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE RANDOM() + 1 > 5 ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|(ABS(RANDOM())) > 5 ;; ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE ABS(RANDOM()) > 5 ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|(RANDSTR(3, RANDOM())) = 'abc' ;; "
                + "ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE RANDSTR(3, RANDOM()) = 'abc' ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : SQL "
                + "compilation error: error line 1 at position 36 | found QUALIFY clause but no window "
                + "function.",
            "EXPLAIN SELECT 1 WHERE RANDOM() > 0 QUALIFY 1 = 1 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|1",
            "EXPLAIN SELECT 1 LIMIT 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|RANDOM() ;; Limit|rowCount: 1 ;; ValuesClause|()",
            "EXPLAIN SELECT RANDOM() LIMIT 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Limit|rowCount: 1 ;; Filter|RANDOM() > 0 ;; "
                + "ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE RANDOM() > 0 LIMIT 1 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|RANDOM() > 0 ;; ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE RANDOM() > 0 ORDER BY 1 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
    }

    @Test
    public void constantGroupKeysAreLeftOut() {
        engine.execute("CREATE OR REPLACE TABLE e07 (n INT)");
        engine.execute("INSERT INTO e07 VALUES (1), (2)");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Aggregate|null ;; TableScan|null",
            "EXPLAIN SELECT 1 FROM e07 GROUP BY 1 ->> SELECT \"operation\", \"expressions\" FROM "
                + "$1");
        assertAnswer("GlobalStats|null ;; Result|E07.N, 1 ;; Aggregate|groupKeys: [E07.N] ;; TableScan|N",
            "EXPLAIN SELECT n, 1 FROM e07 GROUP BY n, 2 ->> SELECT \"operation\", \"expressions\""
                + " FROM $1");
        assertAnswer("GlobalStats|null ;; Result|E07.N, 1 ;; Aggregate|groupKeys: [E07.N] ;; TableScan|N",
            "EXPLAIN SELECT DISTINCT n, 1 FROM e07 ->> SELECT \"operation\", \"expressions\" FROM"
                + " $1");
        assertAnswer("GlobalStats|null ;; Result|'a', RANDOM() ;; Aggregate|groupKeys: [RANDOM()] ;; "
                + "ValuesClause|()",
            "EXPLAIN SELECT DISTINCT 'a', RANDOM() ->> SELECT \"operation\", \"expressions\" FROM"
                + " $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Aggregate|null ;; ValuesClause|()",
            "EXPLAIN SELECT 1 GROUP BY 'x' ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1, RANDOM() ;; Aggregate|groupKeys: [RANDOM()] ;; "
                + "ValuesClause|()",
            "EXPLAIN SELECT 1 AS a, RANDOM() AS b GROUP BY a, b ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1, COUNT(*) ;; Aggregate|aggExprs: [COUNT(*)] ;; "
                + "ValuesClause|()",
            "EXPLAIN SELECT 1 AS a, COUNT(*) GROUP BY a ->> SELECT \"operation\", \"expressions\""
                + " FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|(RANDOM() > 0) AND (RANDOM() > 0) ;; "
                + "ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE RANDOM() > 0 AND RANDOM() > 0 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Aggregate|null ;; TableScan|null",
            "EXPLAIN SELECT 1 FROM e07 GROUP BY 'x' ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
    }
}
