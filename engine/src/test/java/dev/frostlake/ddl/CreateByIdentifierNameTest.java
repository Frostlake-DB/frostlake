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

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@code CREATE <kind> IDENTIFIER('<name>')} names what the statement makes for a database, a schema, a stream, a
 * task, a sequence, a stage, a file format, a tag, a contact, a policy, a function, an external function, a
 * procedure, a dynamic table, an alert and a git repository, as it does for a table and a view: a client that builds
 * the name has the object created — Snowflake's CLI sends {@code CREATE STAGE IF NOT EXISTS IDENTIFIER('db.s.st')} —
 * under IF NOT EXISTS, OR REPLACE, OR ALTER and CLONE alike, and a stream reads its source that way too. The word
 * IDENTIFIER with no whole reference after it is refused where the account refuses it: a database's and a schema's at
 * the token where the reference stops; a policy's at the parenthesis and the token after it; a routine's and a dynamic
 * table's at the value standing where a parameter's or a column's name must, the word naming the object and the
 * parenthesis its list; an alert's at the parenthesis, a statement after it still read; every other kind's at the
 * parenthesis, with nothing reported after it. A database's name of several parts is refused as the account refuses
 * it.
 */
public class CreateByIdentifierNameTest extends MeasuredCellsTestSupport {

    /** The databases the tests make, dropped before and after each. */
    private static final String[] DATABASES = {"FL_CBI_DB1", "FL_CBI_DB2", "fl_cbi_q"};

    private static final String SCHEMA_OBJECTS_ONLY = "REFUSED SQL compilation error: | Object does not exist, or "
        + "operation cannot be performed.";

    @Override
    protected void setupTest() {
        dropDatabases();
    }

    @Override
    protected void teardownTest() {
        dropDatabases();
    }

    private void dropDatabases() {
        for (final String name : DATABASES) {
            engine.execute("DROP DATABASE IF EXISTS \"" + name + "\"");
        }
        engine.execute("USE SCHEMA test_db.test_schema");
    }

    /** A syntax error's answer at a position of the statement's first line. */
    private static String syntaxError(final int position, final String token) {
        return "REFUSED SQL compilation error: | syntax error line 1 at position " + position + " unexpected '"
            + token + "'.";
    }

    @Test
    public void aStageAsSnowflakesCliCreatesIt() {
        assertCells(new String[][] {
            {"CREATE STAGE IF NOT EXISTS IDENTIFIER('test_db.test_schema.cbi_st')",
                "Stage area CBI_ST successfully created."},
            {"CREATE STAGE IF NOT EXISTS IDENTIFIER('test_db.test_schema.cbi_st')",
                "CBI_ST already exists, statement succeeded."},
            {"CREATE STAGE IDENTIFIER('test_db.test_schema.cbi_st')",
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.CBI_ST' already exists."},
            {"CREATE STAGE IF NOT EXISTS IDENTIFIER('test_db.test_schema.cbi_st2') ENCRYPTION = (TYPE = "
                + "'SNOWFLAKE_SSE')", "Stage area CBI_ST2 successfully created."},
            {"CREATE TEMPORARY STAGE IDENTIFIER('cbi_st3')", "Stage area CBI_ST3 successfully created."},
            {"CREATE OR REPLACE STAGE IDENTIFIER('\"cbi_St\"')", "Stage area cbi_St successfully created."},
            {"CREATE STAGE cbi_ext URL = 's3://bucket/path/'", "Stage area CBI_EXT successfully created."},
            {"CREATE STAGE IDENTIFIER('cbi_st4') CLONE cbi_ext", "Stage area CBI_ST4 successfully created."},
            {"CREATE STAGE IDENTIFIER('cbi_st5') CLONE nosuch_src",
                "REFUSED SQL compilation error: | Stage 'TEST_DB.TEST_SCHEMA.NOSUCH_SRC' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SRC."},
            {"CREATE STAGE IDENTIFIER('test_db..cbi_st6')", "Stage area CBI_ST6 successfully created."},
        });
        assertListed("SHOW STAGES LIKE 'CBI_ST6' IN SCHEMA test_db.public");
        assertAnswer("Stage area IDENTIFIER successfully created.", "CREATE STAGE identifier");
    }

    @Test
    public void aDatabaseAndASchema() {
        assertCells(new String[][] {
            {"CREATE DATABASE IF NOT EXISTS IDENTIFIER('fl_cbi_db1')", "Database FL_CBI_DB1 successfully created."},
            {"SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()", "FL_CBI_DB1|PUBLIC"},
            {"CREATE DATABASE IF NOT EXISTS IDENTIFIER('fl_cbi_db1')",
                "FL_CBI_DB1 already exists, statement succeeded."},
            {"USE SCHEMA test_db.test_schema", "Statement executed successfully."},
            {"CREATE DATABASE IF NOT EXISTS IDENTIFIER('test_db')", "TEST_DB already exists, statement succeeded."},
            {"SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()", "TEST_DB|TEST_SCHEMA"},
            {"SET cbi_db = CURRENT_ACCOUNT() || '.fl_cbi_db2'", "Statement executed successfully."},
            {"CREATE TRANSIENT DATABASE IDENTIFIER($cbi_db)", "Database FL_CBI_DB2 successfully created."},
            {"CREATE DATABASE IDENTIFIER('\"fl_cbi_q\"')", "Database fl_cbi_q successfully created."},
            {"CREATE DATABASE IDENTIFIER('fl_cbi_db3') CLONE nosuch_src",
                "REFUSED SQL compilation error: | Database 'NOSUCH_SRC' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_SRC."},
            {"USE SCHEMA test_db.test_schema", "Statement executed successfully."},
            {"CREATE SCHEMA IF NOT EXISTS IDENTIFIER('test_db.cbi_s1')", "Schema CBI_S1 successfully created."},
            {"SELECT CURRENT_SCHEMA()", "CBI_S1"},
            {"CREATE OR ALTER SCHEMA IDENTIFIER('test_db.cbi_s2') COMMENT = 'c'",
                "Schema CBI_S2 successfully created."},
            {"SET cbi_s = CURRENT_ACCOUNT() || '.test_db.cbi_s3'", "Statement executed successfully."},
            {"CREATE SCHEMA IDENTIFIER($cbi_s)", "Schema CBI_S3 successfully created."},
            {"CREATE SCHEMA IDENTIFIER('cbi_s4') CLONE nosuch_src",
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SRC' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SRC."},
            {"CREATE SCHEMA IDENTIFIER('nosuchdb_cbi.s')",
                "REFUSED SQL compilation error: | Database 'NOSUCHDB_CBI' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCHDB_CBI."},
            {"CREATE SCHEMA IDENTIFIER('test_db.test_schema.cbi_x')", SCHEMA_OBJECTS_ONLY},
        });
    }

    @Test
    public void aDatabaseNameOfSeveralParts() {
        assertCells(new String[][] {
            {"CREATE DATABASE fl_cbi_a.fl_cbi_b", SCHEMA_OBJECTS_ONLY},
            {"CREATE DATABASE IDENTIFIER('fl_cbi_a.fl_cbi_b')", SCHEMA_OBJECTS_ONLY},
            {"CREATE DATABASE IF NOT EXISTS fl_cbi_a.fl_cbi_b", SCHEMA_OBJECTS_ONLY},
            {"CREATE OR REPLACE DATABASE fl_cbi_a.fl_cbi_b", SCHEMA_OBJECTS_ONLY},
            {"CREATE DATABASE fl_cbi_a.fl_cbi_b CLONE nosuch_src", SCHEMA_OBJECTS_ONLY},
            {"CREATE DATABASE fl_cbi_a..fl_cbi_b", SCHEMA_OBJECTS_ONLY},
            {"CREATE DATABASE IDENTIFIER('fl_cbi_a.fl_cbi_b.fl_cbi_c')", SCHEMA_OBJECTS_ONLY},
            {"CREATE DATABASE fl_cbi_a.fl_cbi_b.fl_cbi_c.fl_cbi_d",
                "REFUSED SQL compilation error: database identifier 'FL_CBI_A.FL_CBI_B.FL_CBI_C.FL_CBI_D' has too "
                + "many qualifiers"},
        });
    }

    @Test
    public void aTaskASequenceAFileFormatAndAStream() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TASK IDENTIFIER('test_db.test_schema.cbi_tk') AS SELECT 1",
                "Task CBI_TK successfully created."},
            {"CREATE OR ALTER TASK IDENTIFIER('cbi_tk2') AS SELECT 1", "Task CBI_TK2 successfully created."},
            {"CREATE TASK IDENTIFIER('\"cbi_Tk\"') AS SELECT 1", "Task cbi_Tk successfully created."},
        });
        assertListed("SHOW TASKS LIKE 'cbi_Tk'");
        assertCells(new String[][] {
            {"CREATE TASK IDENTIFIER('cbi_tk3') CLONE cbi_tk", "Task CBI_TK3 successfully created."},
            {"CREATE TASK IDENTIFIER('nosuchdb_cbi.s.tk') AS SELECT 1",
                "REFUSED SQL compilation error: | Database 'NOSUCHDB_CBI' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCHDB_CBI."},
            {"CREATE SEQUENCE IDENTIFIER('test_db.test_schema.cbi_sq') START 5 INCREMENT 2",
                "Sequence CBI_SQ successfully created."},
            {"SELECT cbi_sq.NEXTVAL", "5"},
            {"CREATE SEQUENCE IDENTIFIER('test_db.nosuch_s.cbi_sq')",
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S."},
            {"CREATE OR REPLACE FILE FORMAT IDENTIFIER('test_db.test_schema.cbi_ff') TYPE = CSV",
                "File format CBI_FF successfully created."},
            {"CREATE TEMPORARY FILE FORMAT IDENTIFIER('cbi_ff2')", "File format CBI_FF2 successfully created."},
            {"CREATE FILE FORMAT IDENTIFIER('cbi_ff3') CLONE cbi_ff", "File format CBI_FF3 successfully created."},
            {"CREATE TABLE cbi_src (a INT)", "Table CBI_SRC successfully created."},
            {"CREATE STREAM IDENTIFIER('cbi_sm') ON TABLE IDENTIFIER('cbi_src')",
                "Stream CBI_SM successfully created."},
            {"CREATE STREAM IDENTIFIER('cbi_sm2') CLONE cbi_sm", "Stream CBI_SM2 successfully created."},
            {"CREATE OR REPLACE STREAM IDENTIFIER('test_db.test_schema.cbi_sm3') ON TABLE "
                + "IDENTIFIER('test_db.test_schema.nosuch_tb')",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_TB' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_TB."},
            {"CREATE STREAM cbi_sm4 ON VIEW IDENTIFIER('nosuch_v')",
                "REFUSED SQL compilation error: | View 'NOSUCH_V' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_V."},
            {"CREATE STREAM cbi_sm5 ON STAGE IDENTIFIER('nosuch_st')",
                "REFUSED SQL compilation error: | Stage 'TEST_DB.TEST_SCHEMA.NOSUCH_ST' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ST."},
        });
    }

    @Test
    public void aContainerIsMatchedExactly() {
        final String noSchema = "REFUSED SQL compilation error: | Schema 'TEST_DB.\"test_schema\"' does not exist or "
            + "not authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
            + "TEST_DB.\"test_schema\".";
        assertCells(new String[][] {
            {"CREATE TABLE IDENTIFIER('\"test_schema\".cbi_t') (a INT)", noSchema},
            {"CREATE STAGE IDENTIFIER('\"test_schema\".cbi_st')", noSchema},
            {"CREATE SEQUENCE IF NOT EXISTS IDENTIFIER('\"test_schema\".cbi_sq')", noSchema},
            {"CREATE OR REPLACE FILE FORMAT IDENTIFIER('\"test_schema\".cbi_ff')", noSchema},
            {"CREATE TASK IDENTIFIER('\"test_db\".test_schema.cbi_tk') AS SELECT 1",
                "REFUSED SQL compilation error: | Database '\"test_db\"' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE \"test_db\"."},
        });
    }

    @Test
    public void aTagAContactAndThePolicies() {
        assertCells(new String[][] {
            {"CREATE TAG IDENTIFIER('cbi_tag')", "Tag CBI_TAG successfully created."},
            {"CREATE TAG IF NOT EXISTS IDENTIFIER('test_db.test_schema.cbi_tag') COMMENT = 'c'",
                "CBI_TAG already exists, statement succeeded."},
            {"CREATE OR ALTER TAG IDENTIFIER('cbi_tag2')", "Tag CBI_TAG2 successfully created."},
            {"CREATE CONTACT IDENTIFIER('cbi_ct') EMAIL_DISTRIBUTION_LIST = 'a@example.com'",
                "Contact CBI_CT successfully created."},
            {"CREATE MASKING POLICY IDENTIFIER('cbi_mp') AS (v STRING) RETURNS STRING -> v",
                "Masking policy CBI_MP successfully created."},
            {"CREATE OR ALTER MASKING POLICY IDENTIFIER('cbi_mp2') AS (v STRING) RETURNS STRING -> v",
                "Masking policy CBI_MP2 successfully created."},
            {"CREATE ROW ACCESS POLICY IDENTIFIER('cbi_rap') AS (v STRING) RETURNS BOOLEAN -> TRUE",
                "Row access policy 'CBI_RAP' is successfully created."},
            {"CREATE OR ALTER ROW ACCESS POLICY IDENTIFIER('cbi_rap2') AS (v STRING) RETURNS BOOLEAN -> TRUE",
                "Row access policy 'CBI_RAP2' is successfully created."},
            {"CREATE PROJECTION POLICY IDENTIFIER('cbi_pp') AS () RETURNS PROJECTION_CONSTRAINT -> "
                + "PROJECTION_CONSTRAINT(ALLOW => TRUE)", "Projection policy 'CBI_PP' is successfully created."},
            {"CREATE AGGREGATION POLICY IDENTIFIER('cbi_ap') AS () RETURNS AGGREGATION_CONSTRAINT -> "
                + "NO_AGGREGATION_CONSTRAINT()", "Aggregation policy 'CBI_AP' is successfully created."},
            {"CREATE JOIN POLICY IDENTIFIER('cbi_jp') AS () RETURNS JOIN_CONSTRAINT -> "
                + "JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)", "Join policy 'CBI_JP' is successfully created."},
            {"CREATE PRIVACY POLICY IDENTIFIER('cbi_pv') AS () RETURNS PRIVACY_BUDGET -> NO_PRIVACY_POLICY()",
                "Privacy policy 'CBI_PV' successfully created."},
        });
    }

    @Test
    public void aFunctionAndAProcedure() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION IDENTIFIER('test_db.test_schema.cbi_f')(a INT) RETURNS INT AS 'a + 1'",
                "Function CBI_F successfully created."},
            {"SELECT test_db.test_schema.cbi_f(1)", "2"},
            {"CREATE TEMPORARY FUNCTION IDENTIFIER('cbi_tf')() RETURNS INT AS '7'", "Function CBI_TF successfully "
                + "created."},
            {"CREATE SECURE FUNCTION IDENTIFIER('\"cbi_Sf\"')() RETURNS INT AS '8'", "Function cbi_Sf successfully "
                + "created."},
            {"CREATE FUNCTION IDENTIFIER('cbi_f3')(a INT) RETURNS TABLE (b INT) AS 'SELECT a'",
                "Function CBI_F3 successfully created."},
            {"CREATE OR REPLACE PROCEDURE IDENTIFIER('cbi_p')(x INT) RETURNS INT LANGUAGE SQL AS "
                + "'BEGIN RETURN x; END'", "Function CBI_P successfully created."},
            {"CALL cbi_p(5)", "5"},
            {"CREATE FUNCTION IDENTIFIER(a INT) RETURNS INT AS 'a'", "Function IDENTIFIER successfully created."},
        });
    }

    @Test
    public void theWordIdentifierWithoutAWholeReferenceInMoreKinds() {
        assertCells(new String[][] {
            {"CREATE TAG IDENTIFIER('a' || 'b')", syntaxError(21, "(")},
            {"CREATE OR ALTER TAG IDENTIFIER('a' || 'b')", syntaxError(30, "(")},
            {"CREATE TAG IDENTIFIER('a' || 'b'); SELECT 1 +", syntaxError(21, "(")},
            {"CREATE CONTACT IDENTIFIER('a' || 'b') EMAIL_DISTRIBUTION_LIST = 'a@example.com'", syntaxError(25, "(")},
            {"CREATE PROJECTION POLICY IDENTIFIER('a' || 'b') AS () RETURNS PROJECTION_CONSTRAINT -> "
                + "PROJECTION_CONSTRAINT(ALLOW => TRUE)", syntaxError(35, "(") + " | " + line(36, "'a'")},
            {"CREATE AGGREGATION POLICY IDENTIFIER('a' || 'b') AS () RETURNS AGGREGATION_CONSTRAINT -> "
                + "NO_AGGREGATION_CONSTRAINT()", syntaxError(36, "(") + " | " + line(37, "'a'")},
            {"CREATE JOIN POLICY IDENTIFIER('a' || 'b') AS () RETURNS JOIN_CONSTRAINT -> "
                + "JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)", syntaxError(29, "(") + " | " + line(30, "'a'")},
            {"CREATE PRIVACY POLICY IDENTIFIER('a' || 'b') AS () RETURNS PRIVACY_BUDGET -> NO_PRIVACY_POLICY()",
                syntaxError(32, "(") + " | " + line(33, "'a'")},
            {"CREATE FUNCTION IDENTIFIER('a' || 'b')() RETURNS INT AS '1'", syntaxError(27, "'a'")},
            {"CREATE PROCEDURE IDENTIFIER('a' || 'b')() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'",
                syntaxError(28, "'a'")},
            {"CREATE FUNCTION IDENTIFIER('a' || 'b')() RETURNS INT AS '1'; SELECT 1 +",
                syntaxError(27, "'a'") + " | " + line(71, "<EOF>")},
        });
    }

    @Test
    public void aDynamicTableAnAlertAnExternalFunctionAndAGitRepository() {
        final String integration = "REFUSED SQL compilation error: | Integration 'NOSUCH_API_W12' does not exist or "
            + "not authorized. Your primary role <ROLE> must have at least one privilege granted on INTEGRATION "
            + "NOSUCH_API_W12.";
        assertCells(new String[][] {
            {"CREATE DYNAMIC TABLE IDENTIFIER('fl_dt_w12') TARGET_LAG = '1 minute' WAREHOUSE = nosuch_wh_w12 AS "
                + "SELECT 1 AS a", "REFUSED Warehouse 'NOSUCH_WH_W12' does not exist."},
            {"CREATE OR REPLACE DYNAMIC TABLE IDENTIFIER('test_db.test_schema.fl_dt_w12') TARGET_LAG = '1 minute' "
                + "WAREHOUSE = nosuch_wh_w12 AS SELECT 1 AS a", "REFUSED Warehouse 'NOSUCH_WH_W12' does not exist."},
            {"CREATE DYNAMIC TABLE IDENTIFIER('fl_dt3_w12') CLONE nosuch_dt",
                "REFUSED SQL compilation error: | Object 'NOSUCH_DT' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_DT."},
            {"CREATE ALERT IDENTIFIER('fl_al_w12') WAREHOUSE = nosuch_wh_w12 SCHEDULE = '1 minute' IF (EXISTS "
                + "(SELECT 1)) THEN SELECT 1", "REFUSED Nonexistent warehouse NOSUCH_WH_W12 was specified."},
            {"CREATE OR ALTER ALERT IDENTIFIER('fl_al_w12') WAREHOUSE = nosuch_wh_w12 SCHEDULE = '1 minute' IF "
                + "(EXISTS (SELECT 1)) THEN SELECT 1", "REFUSED Nonexistent warehouse NOSUCH_WH_W12 was specified."},
            {"CREATE ALERT IF NOT EXISTS IDENTIFIER('fl_al_w12') WAREHOUSE = nosuch_wh_w12 SCHEDULE = '1 minute' IF "
                + "(EXISTS (SELECT 1)) THEN SELECT 1", "REFUSED Nonexistent warehouse NOSUCH_WH_W12 was specified."},
            {"CREATE ALERT fl_al2_w12 CLONE IDENTIFIER('nosuch_al')",
                "REFUSED SQL compilation error: | Alert 'TEST_DB.TEST_SCHEMA.NOSUCH_AL' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on ALERT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_AL."},
            {"CREATE EXTERNAL FUNCTION IDENTIFIER('fl_ef_w12')(a INT) RETURNS INT API_INTEGRATION = nosuch_api_w12 AS "
                + "'https://example.com/x'", integration},
            {"CREATE OR REPLACE SECURE EXTERNAL FUNCTION IDENTIFIER('test_db.test_schema.fl_ef_w12')(a INT) RETURNS "
                + "INT API_INTEGRATION = nosuch_api_w12 AS 'https://example.com/x'", integration},
            {"CREATE EXTERNAL FUNCTION IDENTIFIER(a INT) RETURNS INT API_INTEGRATION = nosuch_api_w12 AS "
                + "'https://example.com/x'", integration},
            {"CREATE GIT REPOSITORY IDENTIFIER('fl_git_w12') API_INTEGRATION = nosuch_api_w12 ORIGIN = "
                + "'https://github.com/x/y.git'",
                "REFUSED SQL compilation error: | Integration 'NOSUCH_API_W12' does not exist or not authorized."},
            {"CREATE DYNAMIC TABLE IDENTIFIER('a' || 'b') TARGET_LAG = '1 minute' WAREHOUSE = nosuch_wh_w12 AS "
                + "SELECT 1 AS a", syntaxError(32, "'a'")},
            {"CREATE DYNAMIC TABLE IDENTIFIER() TARGET_LAG = '1 minute' WAREHOUSE = nosuch_wh_w12 AS SELECT 1 AS a",
                syntaxError(32, ")")},
            {"CREATE DYNAMIC TABLE IDENTIFIER('a' || 'b') TARGET_LAG = '1 minute' WAREHOUSE = w AS SELECT 1 AS a; "
                + "SELECT 1 +", syntaxError(32, "'a'") + " | " + line(110, "<EOF>")},
            {"CREATE ALERT IDENTIFIER('a' || 'b') WAREHOUSE = nosuch_wh_w12 SCHEDULE = '1 minute' IF (EXISTS "
                + "(SELECT 1)) THEN SELECT 1", syntaxError(23, "(")},
            {"CREATE ALERT IDENTIFIER('a' || 'b') WAREHOUSE = w SCHEDULE = '1 minute' IF (EXISTS (SELECT 1)) THEN "
                + "SELECT 1; SELECT 1 +", syntaxError(23, "(") + " | " + line(120, "<EOF>")},
            {"CREATE ALERT fl_al2_w12 CLONE IDENTIFIER('a' || 'b')", syntaxError(40, "(")},
            {"CREATE GIT REPOSITORY IDENTIFIER('a' || 'b') API_INTEGRATION = nosuch_api_w12 ORIGIN = "
                + "'https://github.com/x/y.git'", syntaxError(32, "(")},
            {"CREATE GIT REPOSITORY IDENTIFIER('a' || 'b') API_INTEGRATION = i ORIGIN = "
                + "'https://github.com/x/y.git'; SELECT 1 +", syntaxError(32, "(")},
        });
    }

    /** One more line of a syntax error's answer, at a position of the statement's first line. */
    private static String line(final int position, final String token) {
        return "syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void theWordIdentifierWithoutAWholeReference() {
        assertCells(new String[][] {
            {"CREATE DATABASE IDENTIFIER('a' || 'b')", syntaxError(31, "||")},
            {"CREATE DATABASE IDENTIFIER('a' || 'b') CLONE src", syntaxError(31, "||")},
            {"CREATE DATABASE IDENTIFIER()", syntaxError(27, ")")},
            {"CREATE DATABASE IDENTIFIER(UPPER('x'))", syntaxError(27, "UPPER")},
            {"CREATE SCHEMA IDENTIFIER('a' || 'b')", syntaxError(29, "||")},
            {"CREATE SCHEMA IDENTIFIER()", syntaxError(25, ")")},
            {"CREATE OR ALTER SCHEMA IDENTIFIER('a' || 'b')", syntaxError(33, "(")},
            {"CREATE STAGE IDENTIFIER('a' || 'b')", syntaxError(23, "(")},
            {"CREATE TEMPORARY STAGE IDENTIFIER('a' || 'b')", syntaxError(33, "(")},
            {"CREATE STAGE IDENTIFIER('a' || 'b') CLONE src", syntaxError(23, "(")},
            {"CREATE STAGE IDENTIFIER()", syntaxError(23, "(")},
            {"CREATE TASK IDENTIFIER('a' || 'b') AS SELECT 1", syntaxError(22, "(")},
            {"CREATE OR ALTER TASK IDENTIFIER('a' || 'b') AS SELECT 1", syntaxError(31, "(")},
            {"CREATE TASK IDENTIFIER(UPPER('x')) AS SELECT 1", syntaxError(22, "(")},
            {"CREATE TASK IDENTIFIER('a' || 'b') CLONE src", syntaxError(22, "(")},
            {"CREATE FILE FORMAT IDENTIFIER('a' || 'b')", syntaxError(29, "(")},
            {"CREATE SEQUENCE IDENTIFIER('a' || 'b')", syntaxError(26, "(")},
            {"CREATE STREAM IDENTIFIER('a' || 'b') ON TABLE t", syntaxError(24, "(")},
            {"CREATE STREAM s ON TABLE IDENTIFIER('a' || 'b')", syntaxError(35, "(")},
            {"CREATE STREAM IDENTIFIER('a' || 'b') CLONE src", syntaxError(24, "(")},
            {"CREATE DATABASE IDENTIFIER('')",
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier ''''"},
            {"CREATE DATABASE IDENTIFIER(12)",
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier '12'"},
        });
    }

    /** Asserts a listing holds a row: the object it names was made where it looks. */
    private void assertListed(final String sql) {
        final String rows = answer(sql);
        assertFalse(rows.isEmpty() || rows.startsWith("REFUSED"), sql + ": " + rows);
    }
}
