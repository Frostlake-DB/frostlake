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
 * What EXPLAIN folds and reads as the account compiles a query (live-verified). A context function reading the
 * statement's clock or its session folds to its value: a timestamp as its instant in UTC to the nanosecond marked Z
 * (a TIMESTAMP_TZ with its offset as {@code tz=} minutes past a day), a time to the nanosecond, a name in quotes. A
 * comparison, AND, OR, NOT, IS NULL and a searched CASE of constants fold to their value, and so does a test whether
 * RANDOM or a row-place generator is null; UNIFORM, NORMAL, ZIPF, RANDSTR and UUID_STRING are tested as written. A
 * WHERE condition over a FROM list that folds to true is dropped, and one that folds to false leaves a Generator of
 * no rows, a select of one relation reading its columns as null. A select of constants over catalog tables reads a
 * Generator of as many rows as they hold together. A WHERE reading a sequence's next value is a Filter over its
 * Getnextval. NORMAL's mean and deviation and ZIPF's exponent are FLOAT, a JavaScript function's call is written as
 * its parameters, and a FROM-less subquery or a query-bodied SQL function answering anew is written as its item.
 */
public class ExplainFoldedLeftoversTest extends MeasuredCellsTestSupport {

    @Test
    public void contextFunctionsIsNullAndConstantConditionsFold() {
        engine.execute("CREATE OR REPLACE PROCEDURE pr07(x NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN "
                + "RETURN x + 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fnq07() RETURNS NUMBER AS 'SELECT RANDOM()'");
        engine.execute("CREATE OR REPLACE FUNCTION fnj07(x DOUBLE) RETURNS DOUBLE LANGUAGE JAVASCRIPT AS 'return"
                + " X + 1;'");
        engine.execute("CREATE OR REPLACE TABLE e07 (n INT)");
        engine.execute("INSERT INTO e07 VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE SEQUENCE sq07");
        engine.execute("SET v07 = 5");
        engine.execute("CREATE OR REPLACE TABLE e08 (m INT NOT NULL)");
        engine.execute("CREATE OR REPLACE TABLE e09 (n INT)");
        engine.execute("INSERT INTO e09 VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE e10 (n INT)");
        engine.execute("CREATE OR REPLACE TABLE e11 (n INT)");
        engine.execute("INSERT INTO e11 VALUES (1), (2), (3)");
        engine.execute("DELETE FROM e11 WHERE n = 1");
        engine.execute("CREATE OR REPLACE VIEW v09 AS SELECT n FROM e09");
        engine.execute("CREATE OR REPLACE FUNCTION fnq2() RETURNS NUMBER AS 'SELECT 1 + RANDOM()'");
        engine.execute("CREATE OR REPLACE FUNCTION fnq3(x NUMBER) RETURNS NUMBER AS 'SELECT x + RANDOM()'");
        engine.execute("CREATE OR REPLACE FUNCTION fnq4() RETURNS NUMBER AS 'RANDOM()'");
        engine.execute("CREATE OR REPLACE FUNCTION fnj2(a DOUBLE, b VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT"
                + " AS 'return B;'");
        engine.execute("CREATE OR REPLACE FUNCTION fnjs(s VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS "
                + "'return S;'");
        engine.execute("CREATE OR REPLACE FUNCTION fnj0() RETURNS DOUBLE LANGUAGE JAVASCRIPT AS 'return 1;'");
        assertAnswer("GlobalStats|null ;; Result|'dddd-dd-dd dd:dd:dd.dddddddddZ' ;; Generator|d",
            "EXPLAIN SELECT CURRENT_TIMESTAMP() ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'dddd-dd-dd dd:dd:dd.dddddddddZ' ;; Generator|d",
            "EXPLAIN SELECT CURRENT_TIMESTAMP ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'dddd-dd-dd dd:dd:dd.dddddddddZ' ;; Generator|d",
            "EXPLAIN SELECT CURRENT_TIMESTAMP(3) ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'dddd-dd-dd dd:dd:dd.dddddddddZ' ;; Generator|d",
            "EXPLAIN SELECT SYSDATE() ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'dddd-dd-dd dd:dd:dd.dddddddddZ' ;; Generator|d",
            "EXPLAIN SELECT GETDATE() ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'dddd-dd-dd dd:dd:dd.dddddddddZ' ;; Generator|d",
            "EXPLAIN SELECT LOCALTIMESTAMP() ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'dddd-dd-dd dd:dd:dd.dddddddddZ' ;; Generator|d",
            "EXPLAIN SELECT SYSTIMESTAMP() ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'dd:dd:dd.ddddddddd' ;; Generator|d",
            "EXPLAIN SELECT CURRENT_TIME() ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'dd:dd:dd.ddddddddd' ;; Generator|d",
            "EXPLAIN SELECT LOCALTIME() ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'USR' ;; Generator|d",
            "EXPLAIN SELECT CURRENT_USER() ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'ROL' ;; Generator|d",
            "EXPLAIN SELECT CURRENT_ROLE() ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|TRUE ;; Generator|d",
            "EXPLAIN SELECT CURRENT_TIMESTAMP() IS NOT NULL ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'dddd-dd-dd' ;; Generator|d",
            "EXPLAIN SELECT CURRENT_TIMESTAMP()::DATE ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'USRx' ;; Generator|d",
            "EXPLAIN SELECT CURRENT_USER() || 'x' ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'USR' ;; Generator|d",
            "EXPLAIN SELECT UPPER(CURRENT_USER()) ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FALSE ;; Generator|d",
            "EXPLAIN SELECT CURRENT_USER() = 'NOPE' ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|d ;; Generator|d",
            "EXPLAIN SELECT 1 WHERE CURRENT_TIMESTAMP() > '2000-01-01' ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|d",
            "EXPLAIN SELECT n FROM e07 WHERE CURRENT_USER() = 'NOPE' ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'dddd-dd-dd dd:dd:dd.dddddddddZ', 'dddd-dd-dd' ;; Generator|d",
            "EXPLAIN SELECT CURRENT_TIMESTAMP(), CURRENT_DATE() ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'dddd-dd-dd dd:dd:dd.dddddddddZ' ;; Generator|d",
            "EXPLAIN SELECT CURRENT_TIMESTAMP() FROM e07 ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'dd:dd:dd.ddddddddd' ;; Generator|d",
            "EXPLAIN SELECT CURRENT_TIME ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FALSE ;; Generator|d",
            "EXPLAIN SELECT SYSDATE() IS NULL ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'USR' ;; Generator|d",
            "EXPLAIN SELECT (SELECT CURRENT_USER()) ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'dddd-dd-dd dd:dd:dd.dddddddddZ' ;; Generator|d",
            "EXPLAIN SELECT CURRENT_TIMESTAMP(0) ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'dddd-dd-dd dd:dd:dd.dddddddddZ' ;; Generator|d",
            "EXPLAIN SELECT LOCALTIMESTAMP ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FALSE ;; Generator|1",
            "EXPLAIN SELECT RANDOM() IS NULL ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|TRUE ;; Generator|1",
            "EXPLAIN SELECT RANDOM() IS NOT NULL ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FALSE ;; Generator|1",
            "EXPLAIN SELECT SEQ4() IS NULL ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|TRUE ;; Generator|1",
            "EXPLAIN SELECT SEQ8() IS NOT NULL ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|UUID_STRING() IS NULL ;; ValuesClause|()",
            "EXPLAIN SELECT UUID_STRING() IS NULL ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|UNIFORM(1, 10, RANDOM()) IS NULL ;; ValuesClause|()",
            "EXPLAIN SELECT UNIFORM(1, 10, RANDOM()) IS NULL ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|NORMAL(0.0, 1.0, RANDOM()) IS NULL ;; ValuesClause|()",
            "EXPLAIN SELECT NORMAL(0, 1, RANDOM()) IS NULL ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|RANDSTR(5, RANDOM()) IS NULL ;; ValuesClause|()",
            "EXPLAIN SELECT RANDSTR(5, RANDOM()) IS NULL ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|ZIPF(1.0, 10, RANDOM()) IS NULL ;; ValuesClause|()",
            "EXPLAIN SELECT ZIPF(1, 10, RANDOM()) IS NULL ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FALSE ;; Generator|1",
            "EXPLAIN SELECT ABS(RANDOM()) IS NULL ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FALSE ;; Generator|1",
            "EXPLAIN SELECT RANDOM() + 1 IS NULL ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|0",
            "EXPLAIN SELECT 1 WHERE RANDOM() IS NULL ->> SELECT \"operation\", \"expressions\" FROM "
                + "$1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|1",
            "EXPLAIN SELECT 1 WHERE RANDOM() IS NOT NULL ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|0",
            "EXPLAIN SELECT n FROM e07 WHERE RANDOM() IS NULL ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FALSE ;; Generator|1",
            "EXPLAIN SELECT RANDOM(42) IS NULL ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FALSE ;; Generator|1",
            "EXPLAIN SELECT SEQ1() IS NULL ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|TRUE ;; Generator|1",
            "EXPLAIN SELECT NOT (RANDOM() IS NULL) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|RANDOM() > 0 ;; ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE RANDOM() IS NULL OR RANDOM() > 0 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FALSE ;; Generator|1",
            "EXPLAIN SELECT CURRENT_TIMESTAMP() IS NULL ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|UUID_STRING() IS NOT NULL ;; ValuesClause|()",
            "EXPLAIN SELECT UUID_STRING() IS NOT NULL ->> SELECT \"operation\", \"expressions\" FROM "
                + "$1");
        assertAnswer("GlobalStats|null ;; Result|E08.M IS NULL ;; TableScan|M",
            "EXPLAIN SELECT m IS NULL FROM e08 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FALSE ;; Generator|1",
            "EXPLAIN SELECT SEQ2() IS NULL ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|2 ;; Generator|0",
            "EXPLAIN SELECT 1 + 1 WHERE RANDOM() IS NULL ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FALSE, 1 ;; Generator|1",
            "EXPLAIN SELECT RANDOM() IS NULL, 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|RANDOM() ;; TableScan|null",
            "EXPLAIN SELECT RANDOM() FROM e07 WHERE SEQ4() IS NOT NULL ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|0",
            "EXPLAIN SELECT 1 FROM e07 WHERE 1 = 2 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|0",
            "EXPLAIN SELECT n FROM e07 WHERE FALSE ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|0",
            "EXPLAIN SELECT n FROM e07 WHERE NULL ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|0",
            "EXPLAIN SELECT n FROM e07 WHERE n > 0 AND 1 = 2 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|E07.N ;; TableScan|N",
            "EXPLAIN SELECT n FROM e07 WHERE 1 = 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|COUNT(*) ;; Aggregate|aggExprs: [COUNT(*)] ;; Generator|0",
            "EXPLAIN SELECT COUNT(*) FROM e07 WHERE 1 = 2 ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|0",
            "EXPLAIN SELECT n FROM e07 WHERE 1 = 2 ORDER BY n ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|A.N ;; Generator|0",
            "EXPLAIN SELECT a.n FROM e07 a JOIN e09 b ON a.n = b.n WHERE 1 = 2 ->> SELECT "
                + "\"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|0",
            "EXPLAIN SELECT n FROM (SELECT n FROM e07 WHERE 1 = 2) ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|0",
            "EXPLAIN SELECT n FROM e07 WHERE CURRENT_DATE() < '2000-01-01' ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|0",
            "EXPLAIN SELECT n FROM e07 WHERE $v07 = 6 ->> SELECT \"operation\", \"expressions\" FROM "
                + "$1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Limit|rowCount: 1 ;; Generator|0",
            "EXPLAIN SELECT n FROM e07 WHERE 1 = 2 LIMIT 1 ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null + 1 ;; Generator|0",
            "EXPLAIN SELECT n + 1 FROM e07 WHERE 1 = 2 ->> SELECT \"operation\", \"expressions\" FROM"
                + " $1");
        assertAnswer("GlobalStats|null ;; Result|E07.N ;; TableScan|N",
            "EXPLAIN SELECT n FROM e07 WHERE n = 1 OR 1 = 1 ->> SELECT \"operation\", \"expressions\""
                + " FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|0",
            "EXPLAIN SELECT n FROM e10 WHERE 1 = 2 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|0",
            "EXPLAIN SELECT n FROM v09 WHERE 1 = 2 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|0",
            "EXPLAIN SELECT DISTINCT n FROM e07 WHERE 1 = 2 ->> SELECT \"operation\", \"expressions\""
                + " FROM $1");
        assertAnswer("GlobalStats|null ;; Result|SUM(null) ;; Aggregate|aggExprs: [SUM(null)] ;; Generator|0",
            "EXPLAIN SELECT SUM(n) FROM e07 WHERE 1 = 2 ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Aggregate|null ;; Generator|2",
            "EXPLAIN SELECT DISTINCT 1 FROM e07 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Aggregate|null ;; Generator|3",
            "EXPLAIN SELECT DISTINCT 1 FROM e09 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|0",
            "EXPLAIN SELECT DISTINCT 1 FROM e10 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'a', 2 ;; Aggregate|null ;; Generator|3",
            "EXPLAIN SELECT DISTINCT 'a', 2 FROM e09 ->> SELECT \"operation\", \"expressions\" FROM "
                + "$1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Aggregate|null ;; Generator|6",
            "EXPLAIN SELECT DISTINCT 1 FROM e09 a, e07 b ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Aggregate|null ;; Generator|2",
            "EXPLAIN SELECT DISTINCT 1 FROM e11 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Aggregate|null ;; TableScan|null",
            "EXPLAIN SELECT 1 FROM e09 GROUP BY 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Aggregate|null ;; Generator|1",
            "EXPLAIN SELECT DISTINCT 1 FROM e09 LIMIT 1 ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|2 ;; Aggregate|null ;; Generator|3",
            "EXPLAIN SELECT DISTINCT 1 + 1 FROM e09 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|RANDOM() ;; Aggregate|groupKeys: [RANDOM()] ;; TableScan|null",
            "EXPLAIN SELECT DISTINCT RANDOM() FROM e09 ->> SELECT \"operation\", \"expressions\" FROM"
                + " $1");
        assertAnswer("GlobalStats|null ;; Result|1, E09.N ;; Aggregate|groupKeys: [E09.N] ;; TableScan|N",
            "EXPLAIN SELECT DISTINCT 1, n FROM e09 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|COUNT(DISTINCT 1) ;; Aggregate|aggExprs: [COUNT(DISTINCT 1)] "
                + ";; TableScan|null",
            "EXPLAIN SELECT COUNT(DISTINCT 1) FROM e09 ->> SELECT \"operation\", \"expressions\" FROM"
                + " $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|\"getNextVal_SQ07\".NEXTVAL > 0 ;; "
                + "Getnextval|SQ07 ;; ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE sq07.NEXTVAL > 0 ->> SELECT \"operation\", \"expressions\" FROM "
                + "$1");
        assertAnswer("GlobalStats|null ;; Result|\"getNextVal_SQ07\".NEXTVAL ;; "
                + "Filter|\"getNextVal_SQ07_0\".NEXTVAL > 0 ;; Getnextval|SQ07 ;; Getnextval|SQ07 ;; "
                + "ValuesClause|()",
            "EXPLAIN SELECT sq07.NEXTVAL WHERE sq07.NEXTVAL > 0 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|E07.N ;; Filter|\"getNextVal_SQ07\".NEXTVAL > 0 ;; "
                + "Getnextval|SQ07 ;; TableScan|N",
            "EXPLAIN SELECT n FROM e07 WHERE sq07.NEXTVAL > 0 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|\"getNextVal_SQ07\".NEXTVAL ;; Getnextval|SQ07 ;; "
                + "ValuesClause|()",
            "EXPLAIN SELECT sq07.NEXTVAL ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|\"getNextVal_SQ07\".NEXTVAL > 0 ;; "
                + "Getnextval|SQ07 ;; ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE sq07.NEXTVAL > 0 AND 1 = 1 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|0",
            "EXPLAIN SELECT 1 WHERE sq07.NEXTVAL > 0 AND 1 = 2 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|\"getNextVal_SQ07\".NEXTVAL IS NOT NULL ;; "
                + "Getnextval|SQ07 ;; ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE sq07.NEXTVAL IS NOT NULL ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|RANDOM() ;; Filter|\"getNextVal_SQ07\".NEXTVAL > 0 ;; "
                + "Getnextval|SQ07 ;; ValuesClause|()",
            "EXPLAIN SELECT RANDOM() WHERE sq07.NEXTVAL > 0 ->> SELECT \"operation\", \"expressions\""
                + " FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|1",
            "EXPLAIN SELECT 1 WHERE (SELECT COUNT(*) FROM e07) > 0 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|NORMAL(0.0, 1.0, RANDOM()) ;; ValuesClause|()",
            "EXPLAIN SELECT NORMAL(0, 1, RANDOM()) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|NORMAL(0.5, 2.0, 5) ;; ValuesClause|()",
            "EXPLAIN SELECT NORMAL(0.5, 2, 5) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|NORMAL(1.0, 2.5, RANDOM()) ;; ValuesClause|()",
            "EXPLAIN SELECT NORMAL(1, 2.5, RANDOM()) ->> SELECT \"operation\", \"expressions\" FROM "
                + "$1");
        assertAnswer("GlobalStats|null ;; Result|ZIPF(1.0, 10, 5) ;; ValuesClause|()",
            "EXPLAIN SELECT ZIPF(1, 10, 5) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|ZIPF(2.5, 10, RANDOM()) ;; ValuesClause|()",
            "EXPLAIN SELECT ZIPF(2.5, 10, RANDOM()) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|NORMAL(TO_DOUBLE(E07.N), 1.0, RANDOM()) ;; TableScan|N",
            "EXPLAIN SELECT NORMAL(n, 1, RANDOM()) FROM e07 ->> SELECT \"operation\", \"expressions\""
                + " FROM $1");
        assertAnswer("GlobalStats|null ;; Result|ZIPF(TO_DOUBLE(E07.N), 10, 5) ;; TableScan|N",
            "EXPLAIN SELECT ZIPF(n, 10, 5) FROM e07 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|NORMAL(-1.0, 1.0, 5) ;; ValuesClause|()",
            "EXPLAIN SELECT NORMAL(-1, 1, 5) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|NORMAL(1.0, 1.0, 5) ;; ValuesClause|()",
            "EXPLAIN SELECT NORMAL(1e0, 1, 5) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|UNIFORM(1.5, 10, 5) ;; ValuesClause|()",
            "EXPLAIN SELECT UNIFORM(1.5, 10, 5) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|E07.N ;; Filter|(NORMAL(0.0, 1.0, RANDOM())) > 0.0 ;; "
                + "TableScan|N",
            "EXPLAIN SELECT n FROM e07 WHERE NORMAL(0, 1, RANDOM()) > 0 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|NORMAL(0.0, 1.0, E07.N) ;; TableScan|N",
            "EXPLAIN SELECT NORMAL(0, 1, n) FROM e07 ->> SELECT \"operation\", \"expressions\" FROM "
                + "$1");
        assertAnswer("GlobalStats|null ;; Result|ZIPF(1.0, E07.N, 5) ;; TableScan|N",
            "EXPLAIN SELECT ZIPF(1, n, 5) FROM e07 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|NORMAL(1.25, 0.5, 5) ;; ValuesClause|()",
            "EXPLAIN SELECT NORMAL(1.25, 0.5, 5) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|ZIPF(1.5, 10, 5) ;; ValuesClause|()",
            "EXPLAIN SELECT ZIPF(1.50, 10, 5) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|NORMAL(2.0, 1.0, 5) ;; ValuesClause|()",
            "EXPLAIN SELECT NORMAL(1 + 1, 1, 5) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FNJ07(X FLOAT) ;; ValuesClause|()",
            "EXPLAIN SELECT fnj07(1) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FNJ07(X FLOAT) ;; TableScan|N",
            "EXPLAIN SELECT fnj07(n) FROM e07 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FNJ2(A FLOAT, B VARCHAR) ;; ValuesClause|()",
            "EXPLAIN SELECT fnj2(1, 'x') ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FNJS(S VARCHAR) ;; ValuesClause|()",
            "EXPLAIN SELECT fnjs('a') ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FNJ0() ;; ValuesClause|()",
            "EXPLAIN SELECT fnj0() ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|(FNJ07(X FLOAT)) > 0.0 ;; ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE fnj07(1) > 0 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|E07.N ;; Filter|(FNJ07(X FLOAT)) > 1.0 ;; TableScan|N",
            "EXPLAIN SELECT n FROM e07 WHERE fnj07(n) > 1 ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|(FNJ07(X FLOAT)) + 1.0 ;; ValuesClause|()",
            "EXPLAIN SELECT fnj07(1) + 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|RANDOM() ;; ValuesClause|()",
            "EXPLAIN SELECT (SELECT RANDOM()) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|UUID_STRING() ;; ValuesClause|()",
            "EXPLAIN SELECT (SELECT UUID_STRING()) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|(RANDOM()) + 1 ;; ValuesClause|()",
            "EXPLAIN SELECT (SELECT RANDOM() + 1) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|RANDOM() ;; ValuesClause|()",
            "EXPLAIN SELECT fnq07() ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 + (RANDOM()) ;; ValuesClause|()",
            "EXPLAIN SELECT fnq2() ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|4 + (RANDOM()) ;; ValuesClause|()",
            "EXPLAIN SELECT fnq3(4) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|E07.N, RANDOM() ;; TableScan|N",
            "EXPLAIN SELECT n, (SELECT RANDOM()) FROM e07 ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|RANDOM() > 0 ;; ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE (SELECT RANDOM()) > 0 ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|RANDOM() ;; ValuesClause|()",
            "EXPLAIN SELECT fnq4() ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|5 ;; Generator|1",
            "EXPLAIN SELECT (SELECT 5) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|(RANDOM()) + 1 ;; ValuesClause|()",
            "EXPLAIN SELECT fnq07() + 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|RANDOM() ;; ValuesClause|()",
            "EXPLAIN SELECT (SELECT RANDOM() AS r) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|RANDOM() ;; TableScan|null",
            "EXPLAIN SELECT fnq07() FROM e07 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|SEQ4() ;; ValuesClause|()",
            "EXPLAIN SELECT (SELECT SEQ4()) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("true",
            "EXPLAIN SELECT CURRENT_TIMESTAMP() ->> SELECT REGEXP_SUBSTR(\"expressions\", ' "
                + "([0-9][0-9]):', 1, 1, 'e') = TO_CHAR(CONVERT_TIMEZONE('UTC', CURRENT_TIMESTAMP()), "
                + "'HH24') FROM $1 WHERE \"operation\" = 'Result'");
        assertAnswer("true",
            "EXPLAIN SELECT SYSDATE() ->> SELECT REGEXP_SUBSTR(\"expressions\", ' ([0-9][0-9]):', 1, "
                + "1, 'e') = TO_CHAR(SYSDATE(), 'HH24') FROM $1 WHERE \"operation\" = 'Result'");
        assertAnswer("true",
            "EXPLAIN SELECT LOCALTIMESTAMP() ->> SELECT REGEXP_SUBSTR(\"expressions\", ' "
                + "([0-9][0-9]):', 1, 1, 'e') = TO_CHAR(SYSDATE(), 'HH24') FROM $1 WHERE \"operation\" = "
                + "'Result'");
        assertAnswer("true",
            "EXPLAIN SELECT CURRENT_USER() ->> SELECT \"expressions\" = '''' || CURRENT_USER() || "
                + "'''' FROM $1 WHERE \"operation\" = 'Result'");
    }

    @Test
    public void foldsCoercesAndReadsGenerators() {
        engine.execute("CREATE OR REPLACE PROCEDURE pr07(x NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN "
                + "RETURN x + 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fnq07() RETURNS NUMBER AS 'SELECT RANDOM()'");
        engine.execute("CREATE OR REPLACE FUNCTION fnj07(x DOUBLE) RETURNS DOUBLE LANGUAGE JAVASCRIPT AS 'return"
                + " X + 1;'");
        engine.execute("CREATE OR REPLACE TABLE e07 (n INT)");
        engine.execute("INSERT INTO e07 VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE SEQUENCE sq07");
        engine.execute("SET v07 = 5");
        engine.execute("CREATE OR REPLACE TABLE e08 (m INT NOT NULL)");
        engine.execute("CREATE OR REPLACE TABLE e09 (n INT)");
        engine.execute("INSERT INTO e09 VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE e10 (n INT)");
        engine.execute("CREATE OR REPLACE TABLE e11 (n INT)");
        engine.execute("INSERT INTO e11 VALUES (1), (2), (3)");
        engine.execute("DELETE FROM e11 WHERE n = 1");
        engine.execute("CREATE OR REPLACE VIEW v09 AS SELECT n FROM e09");
        engine.execute("CREATE OR REPLACE FUNCTION fnq2() RETURNS NUMBER AS 'SELECT 1 + RANDOM()'");
        engine.execute("CREATE OR REPLACE FUNCTION fnq3(x NUMBER) RETURNS NUMBER AS 'SELECT x + RANDOM()'");
        engine.execute("CREATE OR REPLACE FUNCTION fnq4() RETURNS NUMBER AS 'RANDOM()'");
        engine.execute("CREATE OR REPLACE FUNCTION fnj2(a DOUBLE, b VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT"
                + " AS 'return B;'");
        engine.execute("CREATE OR REPLACE FUNCTION fnjs(s VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS "
                + "'return S;'");
        engine.execute("CREATE OR REPLACE FUNCTION fnj0() RETURNS DOUBLE LANGUAGE JAVASCRIPT AS 'return 1;'");
        engine.execute("CREATE OR REPLACE FUNCTION fnjt(a BOOLEAN, b VARIANT, c ARRAY, d DATE, e TIMESTAMP_LTZ, "
                + "f OBJECT, g BINARY, h VARCHAR(10), i STRING, j FLOAT) RETURNS VARCHAR LANGUAGE "
                + "JAVASCRIPT AS 'return H;'");
        engine.execute("CREATE OR REPLACE FUNCTION fnjnt(t TIMESTAMP_NTZ, u TIMESTAMP_TZ, v TIMESTAMP) RETURNS "
                + "VARCHAR LANGUAGE JAVASCRIPT AS 'return \"x\";'");
        assertAnswer("GlobalStats|null ;; Result|FALSE ;; Generator|d",
            "EXPLAIN SELECT CURRENT_TIMESTAMP() < '2000-01-01' ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'dddd-dd-dd' ;; Generator|d",
            "EXPLAIN SELECT CURRENT_DATE() FROM e07 ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'dddd-dd-dd dd:dd:dd.dddddddddZ' ;; Generator|d",
            "EXPLAIN SELECT CURRENT_TIMESTAMP()::TIMESTAMP_NTZ ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'dddd-dd-dd dd:dd:dd.ddd' ;; Generator|d",
            "EXPLAIN SELECT TO_VARCHAR(SYSDATE()) ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'dddd-dd-dd dd:dd:dd.dddddddddZtz=dddd' ;; Generator|d",
            "EXPLAIN SELECT CONVERT_TIMEZONE('UTC', CURRENT_TIMESTAMP()) ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|TRUE ;; Generator|d",
            "EXPLAIN SELECT CURRENT_TIME() > '00:00:00' ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|Edd.N ;; TableScan|N",
            "EXPLAIN SELECT n FROM e07 WHERE CURRENT_TIMESTAMP() > '2000-01-01' ->> SELECT "
                + "\"operation\", REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), "
                + "CURRENT_ROLE(), 'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'USR', Edd.N ;; TableScan|N",
            "EXPLAIN SELECT CURRENT_USER(), n FROM e07 ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'dddd-dd-dd' ;; Generator|d",
            "EXPLAIN SELECT SYSDATE()::DATE ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'dddd-dd-dd dd:dd:dd.dddddddddZ' ;; Generator|d",
            "EXPLAIN SELECT CURRENT_DATE()::TIMESTAMP_LTZ ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(REPLACE(REPLACE(\"expressions\", CURRENT_USER(), 'USR'), CURRENT_ROLE(), "
                + "'ROL'), '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FALSE ;; Generator|1",
            "EXPLAIN SELECT 1 = 2 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|TRUE ;; Generator|1",
            "EXPLAIN SELECT 'a' < 'b' ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FALSE ;; Generator|1",
            "EXPLAIN SELECT 1 IS NULL ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|TRUE ;; Generator|1",
            "EXPLAIN SELECT NULL IS NULL ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'a' ;; Generator|1",
            "EXPLAIN SELECT CASE WHEN 1 = 1 THEN 'a' ELSE 'b' END ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|E07.N = 1 ;; TableScan|N",
            "EXPLAIN SELECT n = 1 AND 1 = 1 FROM e07 ->> SELECT \"operation\", \"expressions\" FROM "
                + "$1");
        assertAnswer("GlobalStats|null ;; Result|'b' ;; Generator|1",
            "EXPLAIN SELECT IFF(1 = 2, 'a', 'b') ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|TRUE ;; Generator|1",
            "EXPLAIN SELECT NOT (1 = 2) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|TRUE ;; Generator|2",
            "EXPLAIN SELECT 1 = 1 FROM e07 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|2",
            "EXPLAIN SELECT 1 FROM e07 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'a', 2 ;; Generator|3",
            "EXPLAIN SELECT 'a', 2 FROM e09 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|2, 'x' ;; Generator|3",
            "EXPLAIN SELECT 1 + 1, 'x' FROM e09 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FALSE ;; Generator|1",
            "EXPLAIN SELECT RANDOM() - 1 IS NULL ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FALSE ;; Generator|1",
            "EXPLAIN SELECT RANDOM() * 2 IS NULL ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FALSE ;; Generator|1",
            "EXPLAIN SELECT -RANDOM() IS NULL ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FALSE ;; Generator|1",
            "EXPLAIN SELECT SEQ4() + SEQ8() IS NULL ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FALSE ;; Generator|2",
            "EXPLAIN SELECT RANDOM() IS NULL FROM e07 ->> SELECT \"operation\", \"expressions\" FROM "
                + "$1");
        assertAnswer("GlobalStats|null ;; Result|ROUND(RANDOM()) IS NULL ;; ValuesClause|()",
            "EXPLAIN SELECT ROUND(RANDOM()) IS NULL ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FALSE ;; Generator|1",
            "EXPLAIN SELECT RANDOM() / 2 IS NULL ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FALSE ;; Generator|1",
            "EXPLAIN SELECT RANDOM() IS NULL AND 1 = 1 ->> SELECT \"operation\", \"expressions\" FROM"
                + " $1");
        assertAnswer("GlobalStats|null ;; Result|FNJT(A BOOLEAN, B VARIANT, C ARRAY, D DATE, E TIMESTAMP_LTZ, "
                + "F OBJECT, G BINARY, H VARCHAR, I VARCHAR, J FLOAT) ;; ValuesClause|()",
            "EXPLAIN SELECT fnjt(TRUE, 1::VARIANT, [1], '2024-01-01'::DATE, "
                + "'2024-01-01'::TIMESTAMP_LTZ, {'a': 1}, 'ab'::BINARY, 'x', 'y', 1.5) ->> SELECT "
                + "\"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|(FNJS(S VARCHAR)) = 'b' ;; ValuesClause|()",
            "EXPLAIN SELECT fnjs('a') = 'b' ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FNJNT(T TIMESTAMP_NTZ, U TIMESTAMP_TZ, V TIMESTAMP_NTZ) ;; "
                + "ValuesClause|()",
            "EXPLAIN SELECT fnjnt('2024-01-01'::TIMESTAMP_NTZ, '2024-01-01'::TIMESTAMP_TZ, "
                + "'2024-01-01'::TIMESTAMP) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|(FNJ07(X FLOAT)) > 1.5 ;; ValuesClause|()",
            "EXPLAIN SELECT fnj07(1) > 1.5 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|UPPER(FNJS(S VARCHAR)) ;; ValuesClause|()",
            "EXPLAIN SELECT UPPER(fnjs('a')) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FNJ07(X FLOAT) ;; ValuesClause|()",
            "EXPLAIN SELECT fnj07(fnj07(1)) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|(NORMAL(0.0, 1.0, 5)) > 0.0 ;; ValuesClause|()",
            "EXPLAIN SELECT NORMAL(0, 1, 5) > 0 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|(ZIPF(1.0, 10, 5)) > 0 ;; ValuesClause|()",
            "EXPLAIN SELECT ZIPF(1, 10, 5) > 0 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|(NORMAL(0.0, 1.0, 5)) + 1.0 ;; ValuesClause|()",
            "EXPLAIN SELECT NORMAL(0, 1, 5) + 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|(NORMAL(0.0, 1.0, RANDOM())) > 0.0 ;; "
                + "ValuesClause|()",
            "EXPLAIN SELECT 1 WHERE NORMAL(0, 1, RANDOM()) > 0 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|\"getNextVal_SQ07\".NEXTVAL, \"getNextVal_SQ07_0\".NEXTVAL ;;"
                + " Getnextval|SQ07 ;; Getnextval|SQ07 ;; ValuesClause|()",
            "EXPLAIN SELECT sq07.NEXTVAL, sq07.NEXTVAL ->> SELECT \"operation\", \"expressions\" FROM"
                + " $1");
        assertAnswer("GlobalStats|null ;; Result|E07.N ;; Filter|\"getNextVal_SQ07\".NEXTVAL > (E07.N) ;; "
                + "Getnextval|SQ07 ;; TableScan|N",
            "EXPLAIN SELECT n FROM e07 WHERE sq07.NEXTVAL > n ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|\"getNextVal_SQ07\".NEXTVAL ;; "
                + "Filter|\"getNextVal_SQ07_0\".NEXTVAL > 0 ;; Getnextval|SQ07 ;; Getnextval|SQ07 ;; "
                + "TableScan|null",
            "EXPLAIN SELECT sq07.NEXTVAL FROM e07 WHERE sq07.NEXTVAL > 0 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null + 1 ;; Generator|0",
            "EXPLAIN SELECT n, n + 1 FROM e07 WHERE FALSE ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|0",
            "EXPLAIN SELECT * FROM e07 WHERE 1 = 2 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|0",
            "EXPLAIN SELECT n AS x FROM e07 WHERE 1 = 2 ORDER BY x ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|COUNT(null) ;; Aggregate|aggExprs: [COUNT(null)] ;; "
                + "Generator|0",
            "EXPLAIN SELECT COUNT(n) FROM e07 WHERE FALSE ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|A.N ;; Generator|0",
            "EXPLAIN SELECT a.n FROM e07 a, e09 b WHERE 1 = 2 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|B.N ;; Generator|0",
            "EXPLAIN SELECT b.n FROM e07 a JOIN e09 b ON a.n = b.n WHERE 1 = 2 ->> SELECT "
                + "\"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|0",
            "EXPLAIN SELECT n FROM e07 WHERE 1 = 2 AND RANDOM() > 0 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|ROW_NUMBER() OVER (ORDER BY E07.N ASC NULLS LAST) ;; "
                + "WindowFunction|ROW_NUMBER() OVER (ORDER BY E07.N ASC NULLS LAST) ;; Generator|0",
            "EXPLAIN SELECT ROW_NUMBER() OVER (ORDER BY n) FROM e07 WHERE FALSE ->> SELECT "
                + "\"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|E07.N ;; Sort|E07.N ASC NULLS LAST ;; TableScan|N, N",
            "EXPLAIN SELECT n FROM e07 WHERE 1 = 1 ORDER BY n ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|E07.N ;; Filter|E07.N > 1 ;; TableScan|N",
            "EXPLAIN SELECT n FROM e07 WHERE 'a' = 'a' AND n > 1 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'k' ;; Generator|0",
            "EXPLAIN SELECT n, 'k' FROM e07 WHERE FALSE ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|0",
            "EXPLAIN SELECT e.n FROM e07 e WHERE 1 = 2 ->> SELECT \"operation\", \"expressions\" FROM"
                + " $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|0",
            "EXPLAIN SELECT n FROM e07 WHERE FALSE GROUP BY n ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|0",
            "EXPLAIN SELECT n FROM e07 WHERE NOT TRUE ->> SELECT \"operation\", \"expressions\" FROM "
                + "$1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|0",
            "EXPLAIN SELECT n FROM e07 WHERE n > 0 AND NULL ->> SELECT \"operation\", \"expressions\""
                + " FROM $1");
        assertAnswer("GlobalStats|null ;; Result|E07.N ;; Filter|RANDOM() > 0 ;; TableScan|N",
            "EXPLAIN SELECT n FROM e07 WHERE RANDOM() > 0 AND 1 = 1 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|MAX(null) ;; Aggregate|aggExprs: [MAX(null)] ;; Generator|0",
            "EXPLAIN SELECT MAX(n) FROM e07 WHERE FALSE ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Limit|rowCount: 0 ;; Generator|0",
            "EXPLAIN SELECT n FROM e07 WHERE FALSE LIMIT 0 ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|0",
            "EXPLAIN SELECT n FROM e07 WHERE n IN (SELECT n FROM e09) AND 1 = 2 ->> SELECT "
                + "\"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Aggregate|null ;; Generator|2",
            "EXPLAIN SELECT DISTINCT 1 FROM e07 WHERE 1 = 1 ->> SELECT \"operation\", \"expressions\""
                + " FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Aggregate|null ;; Generator|3",
            "EXPLAIN SELECT DISTINCT 1 FROM e09 ORDER BY 1 ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Aggregate|null ;; Generator|3",
            "EXPLAIN SELECT DISTINCT 1 FROM e09 LIMIT 5 ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Aggregate|null ;; Generator|2",
            "EXPLAIN SELECT DISTINCT 1 FROM e09 LIMIT 2 ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'dddd-dd-dd' ;; Aggregate|null ;; Generator|d",
            "EXPLAIN SELECT DISTINCT CURRENT_DATE() FROM e09 ->> SELECT \"operation\", "
                + "REGEXP_REPLACE(\"expressions\", '[0-9]', 'd') FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|0",
            "EXPLAIN SELECT DISTINCT 1 FROM e09 WHERE 1 = 2 ->> SELECT \"operation\", \"expressions\""
                + " FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Aggregate|null ;; Generator|18",
            "EXPLAIN SELECT DISTINCT 1 FROM e09, e09 x, e07 y ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|0",
            "EXPLAIN SELECT DISTINCT 1 FROM e10, e09 ->> SELECT \"operation\", \"expressions\" FROM "
                + "$1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|0",
            "EXPLAIN SELECT DISTINCT 1 FROM e09 LIMIT 0 ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Aggregate|null ;; Generator|4",
            "EXPLAIN SELECT DISTINCT 1 FROM TABLE(GENERATOR(ROWCOUNT => 4)) ->> SELECT \"operation\","
                + " \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Aggregate|null ;; Generator|3",
            "EXPLAIN SELECT DISTINCT NULL FROM e09 ->> SELECT \"operation\", \"expressions\" FROM $1");
    }

    @Test
    public void literalTimestampsAndConstantReads() {
        engine.execute("CREATE OR REPLACE PROCEDURE pr07(x NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN "
                + "RETURN x + 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fnq07() RETURNS NUMBER AS 'SELECT RANDOM()'");
        engine.execute("CREATE OR REPLACE FUNCTION fnj07(x DOUBLE) RETURNS DOUBLE LANGUAGE JAVASCRIPT AS 'return"
                + " X + 1;'");
        engine.execute("CREATE OR REPLACE TABLE e07 (n INT)");
        engine.execute("INSERT INTO e07 VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE SEQUENCE sq07");
        engine.execute("SET v07 = 5");
        engine.execute("CREATE OR REPLACE TABLE e08 (m INT NOT NULL)");
        engine.execute("CREATE OR REPLACE TABLE e09 (n INT)");
        engine.execute("INSERT INTO e09 VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE e10 (n INT)");
        engine.execute("CREATE OR REPLACE TABLE e11 (n INT)");
        engine.execute("INSERT INTO e11 VALUES (1), (2), (3)");
        engine.execute("DELETE FROM e11 WHERE n = 1");
        engine.execute("CREATE OR REPLACE VIEW v09 AS SELECT n FROM e09");
        engine.execute("CREATE OR REPLACE FUNCTION fnq2() RETURNS NUMBER AS 'SELECT 1 + RANDOM()'");
        engine.execute("CREATE OR REPLACE FUNCTION fnq3(x NUMBER) RETURNS NUMBER AS 'SELECT x + RANDOM()'");
        engine.execute("CREATE OR REPLACE FUNCTION fnq4() RETURNS NUMBER AS 'RANDOM()'");
        engine.execute("CREATE OR REPLACE FUNCTION fnj2(a DOUBLE, b VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT"
                + " AS 'return B;'");
        engine.execute("CREATE OR REPLACE FUNCTION fnjs(s VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS "
                + "'return S;'");
        engine.execute("CREATE OR REPLACE FUNCTION fnj0() RETURNS DOUBLE LANGUAGE JAVASCRIPT AS 'return 1;'");
        assertAnswer("GlobalStats|null ;; Result|'2024-01-01 10:00:00.000000000Z' ;; Generator|1",
            "EXPLAIN SELECT '2024-01-01 10:00:00'::TIMESTAMP_NTZ ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'2024-01-01 10:00:00.000000000Z' ;; Generator|1",
            "EXPLAIN SELECT TO_TIMESTAMP('2024-01-01 10:00:00') ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'10:00:00.000000000' ;; Generator|1",
            "EXPLAIN SELECT '10:00:00'::TIME ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|E07.N ;; TableScan|N",
            "EXPLAIN SELECT n FROM e07 WHERE '2024-01-01'::TIMESTAMP_NTZ < "
                + "'2025-01-01'::TIMESTAMP_NTZ ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'2024-01-01 08:00:00.000000000Ztz=1560' ;; Generator|1",
            "EXPLAIN SELECT TO_TIMESTAMP_TZ('2024-01-01 10:00:00 +02:00') ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|1",
            "EXPLAIN SELECT NULLIF(1, 1) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|0",
            "EXPLAIN SELECT 1 FROM e10 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|6",
            "EXPLAIN SELECT 1 FROM e07 x, e09 y ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|2",
            "EXPLAIN SELECT 1 FROM e07 WHERE 1 = 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'ab' ;; Generator|2",
            "EXPLAIN SELECT 'a' || 'b' FROM e07 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|0",
            "EXPLAIN SELECT n FROM e07 WHERE TRUE AND FALSE ->> SELECT \"operation\", \"expressions\""
                + " FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; SortWithLimit|sortKey: [E07.N ASC NULLS LAST], "
                + "rowCount: 1 ;; Generator|0",
            "EXPLAIN SELECT n FROM e07 WHERE 1 = 2 ORDER BY n LIMIT 1 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|TRUE ;; Generator|1",
            "EXPLAIN SELECT 1 = 1 AND 2 = 2 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|RANDOM() ;; TableScan|null",
            "EXPLAIN SELECT (SELECT RANDOM()) FROM e07 ->> SELECT \"operation\", \"expressions\" FROM"
                + " $1");
        assertAnswer("GlobalStats|null ;; Result|(RANDOM()) + (RANDOM()) ;; ValuesClause|()",
            "EXPLAIN SELECT fnq07() + fnq07() ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|0",
            "EXPLAIN SELECT x FROM (SELECT n AS x FROM e07) WHERE 1 = 2 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|0",
            "EXPLAIN WITH c AS (SELECT n FROM e07) SELECT n FROM c WHERE FALSE ->> SELECT "
                + "\"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Filter|\"getNextVal_SQ07\".NEXTVAL IS NULL ;; "
                + "Getnextval|SQ07 ;; TableScan|null",
            "EXPLAIN SELECT 1 FROM e07 WHERE sq07.NEXTVAL IS NULL ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Aggregate|null ;; Generator|6",
            "EXPLAIN SELECT DISTINCT 1 FROM e07 x CROSS JOIN e09 y ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|2",
            "EXPLAIN SELECT 1 FROM e07 ORDER BY 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|FNJ07(X FLOAT) ;; Filter|(FNJ07(X FLOAT)) > 0.0 ;; "
                + "TableScan|N",
            "EXPLAIN SELECT fnj07(1) FROM e07 WHERE fnj07(n) > 0 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|NORMAL(0.0, 1.0, RANDOM()) ;; Filter|(NORMAL(0.0, 1.0, "
                + "RANDOM())) > 1.0 ;; TableScan|null",
            "EXPLAIN SELECT NORMAL(0, 1, RANDOM()) FROM e07 WHERE NORMAL(0, 1, RANDOM()) > 1 ->> "
                + "SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'2024-01-02' ;; Generator|1",
            "EXPLAIN SELECT '2024-01-01'::DATE + 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|'2024-01-01' ;; Generator|2",
            "EXPLAIN SELECT TO_DATE('2024-01-01') FROM e07 ->> SELECT \"operation\", \"expressions\" "
                + "FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Aggregate|null ;; Generator|1",
            "EXPLAIN SELECT DISTINCT 1 FROM e07 LIMIT 1 OFFSET 1 ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|1",
            "EXPLAIN SELECT 1 FROM e07 LIMIT 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|E07.N ;; Filter|E07.N > 1 ;; TableScan|N",
            "EXPLAIN SELECT n FROM e07 WHERE n > 1 AND (1 = 1 OR n < 0) ->> SELECT \"operation\", "
                + "\"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|0",
            "EXPLAIN SELECT 1 WHERE CURRENT_ROLE() = 'NOPE' ->> SELECT \"operation\", \"expressions\""
                + " FROM $1");
        assertAnswer("GlobalStats|null ;; Result|null ;; Generator|1",
            "EXPLAIN SELECT CURRENT_TRANSACTION() ->> SELECT \"operation\", \"expressions\" FROM $1");
    }
}
