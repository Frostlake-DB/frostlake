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

package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Semantic views, as a real account answers them (live-verified): CREATE with its logical tables, relationships,
 * facts, dimensions and metrics and the refusals of a definition, the types DESCRIBE reports, GET_DDL, SHOW, the
 * comment, a rename and DROP, and the namespace a semantic view shares with tables and views.
 */
public class SemanticViewTest extends MeasuredCellsTestSupport {

    @Test
    public void semanticViewSurface() {
        engine.execute("CREATE TABLE t1 (a INT, b VARCHAR(20), c NUMBER(10,2), u INT, v DATE)");
        engine.execute("CREATE TABLE t2 (x INT, a INT, y VARCHAR, z INT)");
        engine.execute("CREATE SEMANTIC VIEW sv1 TABLES (t AS t1 PRIMARY KEY (a)) DIMENSIONS (t.d AS t.b)");
        assertAnswer("Semantic view SV1 successfully created.",
            "SELECT \"status\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("REFUSED SQL compilation error: | Object 'SV1' already exists.",
            "CREATE SEMANTIC VIEW sv1 TABLES (t AS t1 PRIMARY KEY (a)) DIMENSIONS (t.d AS t.b)");
        engine.execute("CREATE SEMANTIC VIEW IF NOT EXISTS sv1 TABLES (t AS t1 PRIMARY KEY (a)) DIMENSIONS (t.d AS"
            + " t.b)");
        assertAnswer("SV1 already exists, statement succeeded.",
            "SELECT \"status\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        engine.execute("CREATE OR REPLACE SEMANTIC VIEW sv1 TABLES (t AS t1 PRIMARY KEY (a)) DIMENSIONS (t.d AS "
            + "t.b) COMMENT = 'hello'");
        assertAnswer("REFUSED SQL compilation error: | options IF NOT EXISTS and OR REPLACE are incompatible.",
            "CREATE OR REPLACE SEMANTIC VIEW IF NOT EXISTS sv1 TABLES (t AS t1 PRIMARY KEY (a)) "
            + "DIMENSIONS (t.d AS t.b)");
        answer("SHOW SEMANTIC VIEWS");
        assertAnswer("SV1|TEST_DB|TEST_SCHEMA|hello|ROLE|null|null",
            "SELECT \"name\", \"database_name\", \"schema_name\", \"comment\", \"owner_role_type\", "
            + "\"extension\", \"max_staleness\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        answer("SHOW TERSE SEMANTIC VIEWS LIKE 'SV%' IN SCHEMA TEST_SCHEMA");
        assertAnswer("SV1|SEMANTIC_VIEW|TEST_DB|TEST_SCHEMA",
            "SELECT \"name\", \"kind\", \"database_name\", \"schema_name\" FROM "
            + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("null|null|null|COMMENT|hello ;; TABLE|T|null|BASE_TABLE_DATABASE_NAME|TEST_DB ;; "
            + "TABLE|T|null|BASE_TABLE_SCHEMA_NAME|TEST_SCHEMA ;; TABLE|T|null|BASE_TABLE_NAME|T1 ;; "
            + "TABLE|T|null|PRIMARY_KEY|[\"A\"] ;; DIMENSION|D|T|TABLE|T ;; DIMENSION|D|T|EXPRESSION|t.b "
            + ";; DIMENSION|D|T|DATA_TYPE|VARCHAR(20) ;; DIMENSION|D|T|ACCESS_MODIFIER|PUBLIC",
            "DESCRIBE SEMANTIC VIEW sv1");
        assertAnswer("create or replace semantic view SV1 | \ttables ( | \t\tT as TEST_DB.TEST_SCHEMA.T1 primary"
            + " key (A) | \t) | \tdimensions ( | \t\tT.D as t.b | \t) | \tcomment='hello';",
            "SELECT GET_DDL('SEMANTIC_VIEW', 'sv1')");
        assertAnswer("create or replace semantic view TEST_DB.TEST_SCHEMA.SV1 | \ttables ( | \t\tT as "
            + "TEST_DB.TEST_SCHEMA.T1 primary key (A) | \t) | \tdimensions ( | \t\tT.D as t.b | \t) | "
            + "\tcomment='hello';",
            "SELECT GET_DDL('SEMANTIC VIEW', 'sv1', TRUE)");
        engine.execute("CREATE SEMANTIC VIEW sv2 TABLES (t1 PRIMARY KEY (a) UNIQUE (u) UNIQUE (b, v) WITH SYNONYMS"
            + " = ('first', 'one') COMMENT = 'tc', t2 PRIMARY KEY (x) UNIQUE (z)) RELATIONSHIPS (rb AS t2"
            + " (z) REFERENCES t1 (a), ra AS t2 (a) REFERENCES t1) FACTS (PRIVATE t2.g AS x, t1.f2 AS  "
            + "t1.f1 +  1, t1.f1 AS c*2 COMMENT = 'fc') DIMENSIONS (t2.d2 AS y, t1.d1 AS UPPER( b ) WITH "
            + "SYNONYMS = ('dee'), PUBLIC t1.d0 AS a, t1.dv AS v) METRICS (t2.m2 AS COUNT(*), t1.m1 AS "
            + "SUM(t1.f1), t1.m0 AS AVG(c), t1.mr AS t1.m1, m3 AS t1.m1 + t2.m2, PRIVATE t1.mp AS "
            + "MAX(t1.f2)) COMMENT = 'it''s'");
        assertAnswer("null|null|null|COMMENT|it's ;; DERIVED_METRIC|M3|null|EXPRESSION|t1.m1 + t2.m2 ;; "
            + "DERIVED_METRIC|M3|null|DATA_TYPE|NUMBER(24,2) ;; "
            + "DERIVED_METRIC|M3|null|ACCESS_MODIFIER|PUBLIC ;; "
            + "TABLE|T1|null|BASE_TABLE_DATABASE_NAME|TEST_DB ;; "
            + "TABLE|T1|null|BASE_TABLE_SCHEMA_NAME|TEST_SCHEMA ;; TABLE|T1|null|BASE_TABLE_NAME|T1 ;; "
            + "TABLE|T1|null|SYNONYMS|[\"first\",\"one\"] ;; TABLE|T1|null|PRIMARY_KEY|[\"A\"] ;; "
            + "TABLE|T1|null|UNIQUE_KEY|[[\"U\"],[\"B\",\"V\"]] ;; TABLE|T1|null|COMMENT|tc ;; "
            + "DIMENSION|D0|T1|TABLE|T1 ;; DIMENSION|D0|T1|EXPRESSION|a ;; "
            + "DIMENSION|D0|T1|DATA_TYPE|NUMBER(38,0) ;; DIMENSION|D0|T1|ACCESS_MODIFIER|PUBLIC ;; "
            + "DIMENSION|D1|T1|TABLE|T1 ;; DIMENSION|D1|T1|EXPRESSION|UPPER( b ) ;; "
            + "DIMENSION|D1|T1|DATA_TYPE|VARCHAR(60) ;; DIMENSION|D1|T1|SYNONYMS|[\"dee\"] ;; "
            + "DIMENSION|D1|T1|ACCESS_MODIFIER|PUBLIC ;; DIMENSION|DV|T1|TABLE|T1 ;; "
            + "DIMENSION|DV|T1|EXPRESSION|v ;; DIMENSION|DV|T1|DATA_TYPE|DATE ;; "
            + "DIMENSION|DV|T1|ACCESS_MODIFIER|PUBLIC ;; FACT|F1|T1|TABLE|T1 ;; FACT|F1|T1|EXPRESSION|c*2"
            + " ;; FACT|F1|T1|DATA_TYPE|NUMBER(11,2) ;; FACT|F1|T1|COMMENT|fc ;; "
            + "FACT|F1|T1|ACCESS_MODIFIER|PUBLIC ;; FACT|F2|T1|TABLE|T1 ;; FACT|F2|T1|EXPRESSION|t1.f1 + "
            + " 1 ;; FACT|F2|T1|DATA_TYPE|NUMBER(12,2) ;; FACT|F2|T1|ACCESS_MODIFIER|PUBLIC ;; "
            + "METRIC|M0|T1|TABLE|T1 ;; METRIC|M0|T1|EXPRESSION|AVG(c) ;; "
            + "METRIC|M0|T1|DATA_TYPE|NUMBER(28,8) ;; METRIC|M0|T1|ACCESS_MODIFIER|PUBLIC ;; "
            + "METRIC|M1|T1|TABLE|T1 ;; METRIC|M1|T1|EXPRESSION|SUM(t1.f1) ;; "
            + "METRIC|M1|T1|DATA_TYPE|NUMBER(23,2) ;; METRIC|M1|T1|ACCESS_MODIFIER|PUBLIC ;; "
            + "METRIC|MP|T1|TABLE|T1 ;; METRIC|MP|T1|EXPRESSION|MAX(t1.f2) ;; "
            + "METRIC|MP|T1|DATA_TYPE|NUMBER(12,2) ;; METRIC|MP|T1|ACCESS_MODIFIER|PRIVATE ;; "
            + "METRIC|MR|T1|TABLE|T1 ;; METRIC|MR|T1|EXPRESSION|t1.m1 ;; "
            + "METRIC|MR|T1|DATA_TYPE|NUMBER(23,2) ;; METRIC|MR|T1|ACCESS_MODIFIER|PUBLIC ;; "
            + "TABLE|T2|null|BASE_TABLE_DATABASE_NAME|TEST_DB ;; "
            + "TABLE|T2|null|BASE_TABLE_SCHEMA_NAME|TEST_SCHEMA ;; TABLE|T2|null|BASE_TABLE_NAME|T2 ;; "
            + "TABLE|T2|null|PRIMARY_KEY|[\"X\"] ;; TABLE|T2|null|UNIQUE_KEY|[[\"Z\"]] ;; "
            + "RELATIONSHIP|RA|T2|TABLE|T2 ;; RELATIONSHIP|RA|T2|REF_TABLE|T1 ;; "
            + "RELATIONSHIP|RA|T2|FOREIGN_KEY|[\"A\"] ;; RELATIONSHIP|RA|T2|REF_KEY|[\"A\"] ;; "
            + "RELATIONSHIP|RB|T2|TABLE|T2 ;; RELATIONSHIP|RB|T2|REF_TABLE|T1 ;; "
            + "RELATIONSHIP|RB|T2|FOREIGN_KEY|[\"Z\"] ;; RELATIONSHIP|RB|T2|REF_KEY|[\"A\"] ;; "
            + "DIMENSION|D2|T2|TABLE|T2 ;; DIMENSION|D2|T2|EXPRESSION|y ;; "
            + "DIMENSION|D2|T2|DATA_TYPE|VARCHAR(16777216) ;; DIMENSION|D2|T2|ACCESS_MODIFIER|PUBLIC ;; "
            + "FACT|G|T2|TABLE|T2 ;; FACT|G|T2|EXPRESSION|x ;; FACT|G|T2|DATA_TYPE|NUMBER(38,0) ;; "
            + "FACT|G|T2|ACCESS_MODIFIER|PRIVATE ;; METRIC|M2|T2|TABLE|T2 ;; "
            + "METRIC|M2|T2|EXPRESSION|COUNT(*) ;; METRIC|M2|T2|DATA_TYPE|NUMBER(18,0) ;; "
            + "METRIC|M2|T2|ACCESS_MODIFIER|PUBLIC",
            "DESCRIBE SEMANTIC VIEW sv2");
        assertAnswer("create or replace semantic view SV2 | \ttables ( | \t\tTEST_DB.TEST_SCHEMA.T1 primary key "
            + "(A) unique (U) unique (B,V) with synonyms=('first','one') comment='tc', | "
            + "\t\tTEST_DB.TEST_SCHEMA.T2 primary key (X) unique (Z) | \t) | \trelationships ( | \t\tRB "
            + "as T2(Z) references T1(A), | \t\tRA as T2(A) references T1(A) | \t) | \tfacts ( | "
            + "\t\tT1.F2 as t1.f1 +  1, | \t\tT1.F1 as c*2 comment='fc', | \t\tPRIVATE T2.G as x | \t) | "
            + "\tdimensions ( | \t\tT1.D1 as UPPER( b ) with synonyms=('dee'), | \t\tT1.D0 as a, | "
            + "\t\tT1.DV as v, | \t\tT2.D2 as y | \t) | \tmetrics ( | \t\tT2.M2 as COUNT(*), | \t\tT1.M1 "
            + "as SUM(t1.f1), | \t\tT1.M0 as AVG(c), | \t\tT1.MR as t1.m1, | \t\tM3 as t1.m1 + t2.m2, | "
            + "\t\tPRIVATE T1.MP as MAX(t1.f2) | \t) | \tcomment='it''s';",
            "SELECT GET_DDL('SEMANTIC_VIEW', 'sv2')");
        engine.execute("CREATE SEMANTIC VIEW \"sv Two\" TABLES (al AS TEST_DB.TEST_SCHEMA.t1 PRIMARY KEY (a)) "
            + "DIMENSIONS (al.\"dQ\" AS al.b)");
        assertAnswer("TABLE|AL|null|BASE_TABLE_DATABASE_NAME|TEST_DB ;; "
            + "TABLE|AL|null|BASE_TABLE_SCHEMA_NAME|TEST_SCHEMA ;; TABLE|AL|null|BASE_TABLE_NAME|T1 ;; "
            + "TABLE|AL|null|PRIMARY_KEY|[\"A\"] ;; DIMENSION|dQ|AL|TABLE|AL ;; "
            + "DIMENSION|dQ|AL|EXPRESSION|al.b ;; DIMENSION|dQ|AL|DATA_TYPE|VARCHAR(20) ;; "
            + "DIMENSION|dQ|AL|ACCESS_MODIFIER|PUBLIC",
            "DESC SEMANTIC VIEW \"sv Two\"");
        assertAnswer("create or replace semantic view \"sv Two\" | \ttables ( | \t\tAL as TEST_DB.TEST_SCHEMA.T1"
            + " primary key (A) | \t) | \tdimensions ( | \t\tAL.\"dQ\" as al.b | \t);",
            "SELECT GET_DDL('SEMANTIC_VIEW', '\"sv Two\"')");
        engine.execute("ALTER SEMANTIC VIEW sv1 SET COMMENT = 'x'");
        answer("SHOW SEMANTIC VIEWS LIKE 'SV1'");
        assertAnswer("SV1|x",
            "SELECT \"name\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        engine.execute("ALTER SEMANTIC VIEW sv1 UNSET COMMENT");
        answer("SHOW SEMANTIC VIEWS LIKE 'SV1'");
        assertAnswer("SV1|null",
            "SELECT \"name\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        engine.execute("COMMENT ON SEMANTIC VIEW sv1 IS 'cm'");
        answer("SHOW SEMANTIC VIEWS LIKE 'SV1'");
        assertAnswer("SV1|cm",
            "SELECT \"name\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        engine.execute("ALTER SEMANTIC VIEW sv1 RENAME TO sv3");
        answer("SHOW SEMANTIC VIEWS");
        assertAnswer("SV2|it's ;; SV3|cm ;; sv Two|null",
            "SELECT \"name\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        answer("ALTER SEMANTIC VIEW nosuch SET COMMENT = 'x'");
        assertAnswer("REFUSED SQL compilation error: | Object 'T1' already exists.",
            "ALTER SEMANTIC VIEW sv3 RENAME TO t1");
        assertAnswer("REFUSED SQL compilation error: | Object 'SV2' already exists.",
            "ALTER SEMANTIC VIEW sv3 RENAME TO sv2");
        engine.execute("ALTER SEMANTIC VIEW IF EXISTS nosuch RENAME TO sv8");
        answer("ALTER SEMANTIC VIEW nosuch RENAME TO sv8");
        engine.execute("DROP SEMANTIC VIEW sv3");
        assertAnswer("SV3 successfully dropped.",
            "SELECT \"status\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        answer("DROP SEMANTIC VIEW sv3");
        engine.execute("DROP SEMANTIC VIEW IF EXISTS sv3");
        assertAnswer("Drop statement executed successfully (SV3 already dropped).",
            "SELECT \"status\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        answer("DESCRIBE SEMANTIC VIEW sv3");
        answer("SELECT GET_DDL('SEMANTIC_VIEW', 'sv3')");
        assertAnswer("REFUSED SQL compilation error: | Object 'SV2' already exists as SEMANTIC_VIEW",
            "CREATE TABLE sv2 (a INT)");
        assertAnswer("REFUSED SQL compilation error: | Object 'SV2' already exists as SEMANTIC_VIEW",
            "CREATE VIEW sv2 AS SELECT 1 a");
        assertAnswer("REFUSED SQL compilation error: Object found is of type 'SEMANTIC_VIEW', not specified type"
            + " 'VIEW'.",
            "DROP VIEW sv2");
        assertAnswer("REFUSED SQL compilation error: Object found is of type 'SEMANTIC_VIEW', not specified type"
            + " 'TABLE'.",
            "DROP TABLE sv2");
        assertAnswer("REFUSED SQL compilation error: | Object 'T1' already exists as TABLE",
            "CREATE SEMANTIC VIEW t1 TABLES (t AS t1 PRIMARY KEY (a)) DIMENSIONS (t.d AS t.b)");
        assertAnswer("REFUSED SQL compilation error: error line {0} at position {1} | invalid identifier '{2}'",
            "DESCRIBE SEMANTIC VIEW t1");
        assertAnswer("REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type "
            + "'SEMANTIC_VIEW'.",
            "DROP SEMANTIC VIEW t1");
        assertAnswer("REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type "
            + "'SEMANTIC_VIEW'.",
            "COMMENT ON SEMANTIC VIEW t1 IS 'x'");
        answer("SELECT GET_DDL('SEMANTIC_VIEW', 't1')");
        engine.execute("CREATE SEMANTIC VIEW sve1 TABLES (t AS t1) DIMENSIONS (t.d AS t.b)");
        answer("CREATE SEMANTIC VIEW sve2 TABLES (t AS nosuch PRIMARY KEY (a)) DIMENSIONS (t.d AS t.b)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 78 | invalid identifier 'T.ZZ'",
            "CREATE SEMANTIC VIEW sve3 TABLES (t AS t1 PRIMARY KEY (a)) DIMENSIONS (t.d AS t.zz)");
        assertAnswer("REFUSED SQL compilation error: | No queryable expression is defined in the semantic view.",
            "CREATE SEMANTIC VIEW sve4 TABLES (t AS t1 PRIMARY KEY (a))");
        assertAnswer("REFUSED SQL compilation error: | Invalid table name 'U' in the dimensions definition.",
            "CREATE SEMANTIC VIEW sve5 TABLES (t AS t1 PRIMARY KEY (a)) DIMENSIONS (u.d AS t.b)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 55 | invalid identifier 'ZZ'",
            "CREATE SEMANTIC VIEW sve6 TABLES (t AS t1 PRIMARY KEY (zz)) DIMENSIONS (t.d AS t.b)");
        assertAnswer("REFUSED SQL compilation error: | Duplicate expression name 'T1.D'.",
            "CREATE SEMANTIC VIEW sve7 TABLES (t1 PRIMARY KEY (a)) DIMENSIONS (t1.d AS b, t1.d AS c)");
        assertAnswer("REFUSED SQL compilation error: | duplicate alias 'T1'",
            "CREATE SEMANTIC VIEW sve8 TABLES (t1 PRIMARY KEY (a), t1 PRIMARY KEY (a)) DIMENSIONS (t1.d"
            + " AS b)");
        assertAnswer("REFUSED SQL compilation error: | Duplicate expression name 'T1.D'.",
            "CREATE SEMANTIC VIEW sve9 TABLES (t1 PRIMARY KEY (a)) FACTS (t1.d AS b) DIMENSIONS (t1.d "
            + "AS c)");
        assertAnswer("REFUSED SQL compilation error: | Invalid dimension definition for 'T1.D': A dimension must"
            + " directly refer to a column from the same table without an aggregate.",
            "CREATE SEMANTIC VIEW sve10 TABLES (t1 PRIMARY KEY (a)) DIMENSIONS (t1.d AS SUM(c))");
        assertAnswer("REFUSED SQL compilation error: | Invalid metric definition for 'T1.M': A metric must have "
            + "a single aggregate over a column from the same table.",
            "CREATE SEMANTIC VIEW sve11 TABLES (t1 PRIMARY KEY (a)) METRICS (t1.m AS c)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 67 | Invalid definition for 'D': "
            + "Row-level expressions must be qualified unless they are LOD. An LOD expression must "
            + "specify FIXED, INCLUDE, or EXCLUDE DIMENSIONS.",
            "CREATE SEMANTIC VIEW sve12 TABLES (t1 PRIMARY KEY (a)) DIMENSIONS (d AS b)");
        assertAnswer("REFUSED SQL compilation error: | The relationship 'T2 REFERENCES T1' does not specify "
            + "foreign keys or referenced keys.",
            "CREATE SEMANTIC VIEW sve13 TABLES (t1, t2 PRIMARY KEY (x)) RELATIONSHIPS (t2 (a) "
            + "REFERENCES t1) DIMENSIONS (t1.d AS b)");
        assertAnswer("REFUSED SQL compilation error: | Invalid table name 'T9' in the RELATIONSHIPS definition.",
            "CREATE SEMANTIC VIEW sve14 TABLES (t1 PRIMARY KEY (a)) RELATIONSHIPS (t1 (a) REFERENCES "
            + "t9) DIMENSIONS (t1.d AS b)");
        assertAnswer("REFUSED SQL compilation error: error line 2 at position 23 | invalid identifier 'ZZ'",
            "CREATE SEMANTIC VIEW sve15 TABLES (t1 PRIMARY KEY (a)) DIMENSIONS (t1.d AS b)\n  METRICS "
            + "(t1.m AS SUM(zz))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 78 unexpected "
            + "'DIMENSIONS'.",
            "CREATE SEMANTIC VIEW sve16 TABLES (t1 PRIMARY KEY (a)) DIMENSIONS (t1.d AS b) DIMENSIONS "
            + "(t1.e AS b)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 80 unexpected "
            + "'DIMENSIONS'.",
            "CREATE SEMANTIC VIEW sve17 TABLES (t1 PRIMARY KEY (a)) METRICS (t1.m AS SUM(c)) DIMENSIONS"
            + " (t1.d AS b)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ')'.",
            "CREATE SEMANTIC VIEW sve18 TABLES () DIMENSIONS (t1.d AS b)");
        answer("CREATE SEMANTIC VIEW nodb.public.sve19 TABLES (t1 PRIMARY KEY (a)) DIMENSIONS (t1.d AS b)");
        assertAnswer("REFUSED SQL compilation error: | Invalid fact definition for 'T1.F': A fact must directly "
            + "refer to a column from the same table without an aggregate.",
            "CREATE SEMANTIC VIEW sve20 TABLES (t1 PRIMARY KEY (a)) FACTS (t1.f AS SUM(c))");
        assertAnswer("REFUSED SQL compilation error: error line 3 at position 8 | invalid identifier 'NOSUCH'",
            "CREATE SEMANTIC VIEW sve21 TABLES (t1 PRIMARY KEY (a))\n  FACTS (t1.f AS\n    c + nosuch)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 74 | invalid identifier 'ZZ'",
            "CREATE SEMANTIC VIEW sve22 TABLES (t1 PRIMARY KEY (a)) RELATIONSHIPS (t1 (zz) REFERENCES "
            + "t1) DIMENSIONS (t1.d AS b)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 64 | invalid identifier 'T2.X'",
            "CREATE SEMANTIC VIEW sve23 TABLES (t1, t2) METRICS (t1.m AS SUM(t2.x))");
        assertAnswer("REFUSED SQL compilation error: | Cyclic reference of expressions is not allowed. Cycle "
            + "detected: [T1.F,T1.G]",
            "CREATE SEMANTIC VIEW sve24 TABLES (t1) FACTS (t1.f AS t1.g, t1.g AS t1.f)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 75 | invalid identifier 'T2.Y'",
            "CREATE SEMANTIC VIEW sve25 TABLES (t1 PRIMARY KEY (a)) DIMENSIONS (t1.d AS t2.y)");
        engine.execute("CREATE SEMANTIC VIEW sve26 TABLES (t1) FACTS (t1.f AS c) COPY GRANTS");
        assertAnswer("TABLE|T1|null|BASE_TABLE_DATABASE_NAME|TEST_DB ;; "
            + "TABLE|T1|null|BASE_TABLE_SCHEMA_NAME|TEST_SCHEMA ;; TABLE|T1|null|BASE_TABLE_NAME|T1 ;; "
            + "FACT|F|T1|TABLE|T1 ;; FACT|F|T1|EXPRESSION|c ;; FACT|F|T1|DATA_TYPE|NUMBER(10,2) ;; "
            + "FACT|F|T1|ACCESS_MODIFIER|PUBLIC",
            "DESCRIBE SEMANTIC VIEW sve26");
    }
}
