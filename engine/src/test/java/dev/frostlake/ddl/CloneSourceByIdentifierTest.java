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
 * The object a CLONE copies, and the table a LIKE takes its columns from, may be named through IDENTIFIER() as the
 * created name may: a table, an event table, a dynamic table, a schema, a database, a stream, a task, a sequence, a
 * stage and a file format alike, a missing source refused for itself as a written one is. The word IDENTIFIER with no
 * whole reference after it is refused at the parenthesis for every kind, a database's and a schema's too, with nothing
 * reported after it. A database's source of several parts is refused as a database's own name is, a schema's of more
 * parts than two unless its first is this account's locator. A missing file format or stream written bare is named as
 * written, one written by a path in full.
 */
public class CloneSourceByIdentifierTest extends MeasuredCellsTestSupport {

    /** The databases the tests make, dropped before and after each. */
    private static final String[] DATABASES = {"FL_CBS_DB1", "FL_CBS_DB2"};

    private static final String NAMES_NOTHING = "REFUSED SQL compilation error: | Object does not exist, or "
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

    /** A missing object's refusal, the role masked. */
    private static String missing(final String kind, final String name, final String privilegeKind) {
        return "REFUSED SQL compilation error: | " + kind + " '" + name + "' does not exist or not authorized. Your "
            + "primary role <ROLE> must have at least one privilege granted on " + privilegeKind + " " + name + ".";
    }

    @Test
    public void everyKindCopiesASourceGivenThroughIdentifier() {
        engine.execute("CREATE TABLE cs_t (a INT)");
        engine.execute("INSERT INTO cs_t VALUES (1)");
        engine.execute("CREATE SEQUENCE cs_sq");
        engine.execute("CREATE FILE FORMAT cs_ff TYPE = CSV");
        engine.execute("CREATE TASK cs_tk AS SELECT 1");
        engine.execute("CREATE STAGE cs_ext URL = 's3://bucket/path/'");
        engine.execute("CREATE STREAM cs_sm ON TABLE cs_t");
        engine.execute("CREATE SCHEMA cs_sch");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("SET cs_src = 'cs_t'");
        assertCells(new String[][] {
            {"CREATE TABLE cs_t2 CLONE IDENTIFIER('cs_t')", "Table CS_T2 successfully created."},
            {"SELECT COUNT(*) FROM cs_t2", "1"},
            {"CREATE TABLE cs_t3 CLONE IDENTIFIER('test_db.test_schema.cs_t')", "Table CS_T3 successfully created."},
            {"CREATE TABLE IDENTIFIER('cs_t4') CLONE IDENTIFIER($cs_src)", "Table CS_T4 successfully created."},
            {"CREATE TABLE cs_t5 LIKE IDENTIFIER('cs_t')", "Table CS_T5 successfully created."},
            {"SELECT COUNT(*) FROM cs_t5", "0"},
            {"CREATE TABLE cs_t6 CLONE IDENTIFIER('nosuch_t')", missing("Object", "NOSUCH_T", "TABLE")},
            {"CREATE TABLE cs_t6 LIKE IDENTIFIER('nosuch_t')", missing("Object", "NOSUCH_T", "TABLE")},
            {"CREATE TABLE cs_t6 CLONE IDENTIFIER('\"cs_q\"')", missing("Object", "\"cs_q\"", "TABLE")},
            {"CREATE EVENT TABLE cs_et CLONE IDENTIFIER('nosuch_et')", missing("Object", "NOSUCH_ET", "TABLE")},
            {"CREATE DYNAMIC TABLE cs_dt CLONE IDENTIFIER('nosuch_dt')", missing("Object", "NOSUCH_DT", "TABLE")},
            {"CREATE SEQUENCE cs_sq2 CLONE IDENTIFIER('cs_sq')", "Sequence CS_SQ2 successfully created."},
            {"CREATE SEQUENCE cs_sq3 CLONE IDENTIFIER('nosuch_sq')",
                missing("Sequence", "TEST_DB.TEST_SCHEMA.NOSUCH_SQ", "SEQUENCE")},
            {"CREATE FILE FORMAT cs_ff2 CLONE IDENTIFIER('cs_ff')", "File format CS_FF2 successfully created."},
            {"CREATE TASK cs_tk2 CLONE IDENTIFIER('cs_tk')", "Task CS_TK2 successfully created."},
            {"CREATE TASK cs_tk3 CLONE IDENTIFIER('nosuch_tk')",
                missing("Task", "TEST_DB.TEST_SCHEMA.NOSUCH_TK", "TASK")},
            {"CREATE STAGE cs_st2 CLONE IDENTIFIER('cs_ext')", "Stage area CS_ST2 successfully created."},
            {"CREATE STREAM cs_sm2 CLONE IDENTIFIER('cs_sm')", "Stream CS_SM2 successfully created."},
            {"CREATE SCHEMA cs_sch2 CLONE IDENTIFIER('cs_sch')", "Schema CS_SCH2 successfully created."},
            {"USE SCHEMA test_db.test_schema", "Statement executed successfully."},
            {"CREATE DATABASE fl_cbs_db1 CLONE IDENTIFIER('test_db')", "Database FL_CBS_DB1 successfully created."},
        });
    }

    @Test
    public void aMissingFileFormatOrStreamIsNamedAsWritten() {
        assertCells(new String[][] {
            {"CREATE FILE FORMAT cs_ff4 CLONE nosuch_src", missing("File format", "NOSUCH_SRC", "FILE FORMAT")},
            {"CREATE FILE FORMAT cs_ff4 CLONE IDENTIFIER('nosuch_src')",
                missing("File format", "NOSUCH_SRC", "FILE FORMAT")},
            {"CREATE FILE FORMAT cs_ff4 CLONE \"nosuch_q\"", missing("File format", "\"nosuch_q\"", "FILE FORMAT")},
            {"CREATE FILE FORMAT cs_ff4 CLONE test_db.test_schema.nosuch_src",
                missing("File format", "TEST_DB.TEST_SCHEMA.NOSUCH_SRC", "FILE FORMAT")},
            {"CREATE STREAM cs_sm4 CLONE nosuch_src", missing("Stream", "NOSUCH_SRC", "STREAM")},
            {"CREATE STREAM cs_sm4 CLONE IDENTIFIER('nosuch_src')", missing("Stream", "NOSUCH_SRC", "STREAM")},
            {"CREATE STREAM cs_sm4 CLONE test_schema.nosuch_src",
                missing("Stream", "TEST_DB.TEST_SCHEMA.NOSUCH_SRC", "STREAM")},
        });
    }

    @Test
    public void aSourceOfSeveralParts() {
        engine.execute("CREATE SCHEMA cs_src_s");
        engine.execute("USE SCHEMA test_db.test_schema");
        assertCells(new String[][] {
            {"CREATE DATABASE fl_cbs_db2 CLONE fl_cbs_a.fl_cbs_b", NAMES_NOTHING},
            {"CREATE DATABASE fl_cbs_db2 CLONE fl_cbs_a.fl_cbs_b.fl_cbs_c.fl_cbs_d",
                "REFUSED SQL compilation error: database identifier 'FL_CBS_A.FL_CBS_B.FL_CBS_C.FL_CBS_D' has too many "
                + "qualifiers"},
            {"CREATE DATABASE fl_cbs_db2 CLONE \"test_db\"",
                "REFUSED SQL compilation error: | Database '\"test_db\"' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE \"test_db\"."},
            {"SET cs_accsrc = CURRENT_ACCOUNT() || '.test_db'", "Statement executed successfully."},
            {"CREATE DATABASE fl_cbs_db2 CLONE IDENTIFIER($cs_accsrc)", "Database FL_CBS_DB2 successfully created."},
            {"USE SCHEMA test_db.test_schema", "Statement executed successfully."},
            {"CREATE SCHEMA cs_y1 CLONE test_db.cs_src_s", "Schema CS_Y1 successfully created."},
            {"USE SCHEMA test_db.test_schema", "Statement executed successfully."},
            {"CREATE SCHEMA cs_y2 CLONE fl_cbs_a.fl_cbs_b.fl_cbs_c", NAMES_NOTHING},
            {"CREATE SCHEMA cs_y2 CLONE fl_cbs_a.fl_cbs_b.fl_cbs_c.fl_cbs_d", NAMES_NOTHING},
            {"SET cs_accsch = CURRENT_ACCOUNT() || '.test_db.cs_src_s'", "Statement executed successfully."},
            {"CREATE SCHEMA cs_y3 CLONE IDENTIFIER($cs_accsch)", "Schema CS_Y3 successfully created."},
            {"USE SCHEMA test_db.test_schema", "Statement executed successfully."},
            {"CREATE SCHEMA cs_y4 CLONE nosuchdb_cbs.s",
                "REFUSED SQL compilation error: | Database 'NOSUCHDB_CBS' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCHDB_CBS."},
            {"CREATE TABLE cs_t1 CLONE fl_cbs_a.b.c.d", NAMES_NOTHING},
            {"CREATE TABLE cs_t1 LIKE fl_cbs_a.b.c.d", NAMES_NOTHING},
            {"CREATE STAGE cs_s1 CLONE fl_cbs_a.b.c.d", NAMES_NOTHING},
        });
    }

    @Test
    public void theWordIdentifierWithoutAWholeReference() {
        assertCells(new String[][] {
            {"CREATE TABLE cs_b1 CLONE IDENTIFIER('a' || 'b')", syntaxError(35, "(")},
            {"CREATE SEQUENCE cs_b2 CLONE IDENTIFIER('a' || 'b')", syntaxError(38, "(")},
            {"CREATE FILE FORMAT cs_b3 CLONE IDENTIFIER('a' || 'b')", syntaxError(41, "(")},
            {"CREATE TASK cs_b4 CLONE IDENTIFIER('a' || 'b')", syntaxError(34, "(")},
            {"CREATE STAGE cs_b5 CLONE IDENTIFIER('a' || 'b')", syntaxError(35, "(")},
            {"CREATE STREAM cs_b6 CLONE IDENTIFIER('a' || 'b')", syntaxError(36, "(")},
            {"CREATE SCHEMA cs_b7 CLONE IDENTIFIER('a' || 'b')", syntaxError(36, "(")},
            {"CREATE DATABASE cs_b8 CLONE IDENTIFIER('a' || 'b')", syntaxError(38, "(")},
            {"CREATE EVENT TABLE cs_b9 CLONE IDENTIFIER('a' || 'b')", syntaxError(41, "(")},
            {"CREATE DYNAMIC TABLE cs_b10 CLONE IDENTIFIER('a' || 'b')", syntaxError(44, "(")},
            {"CREATE TABLE cs_b11 LIKE IDENTIFIER('a' || 'b')", syntaxError(35, "(")},
            {"CREATE TABLE cs_b12 CLONE IDENTIFIER()", syntaxError(36, "(")},
            {"CREATE DATABASE cs_b13 CLONE IDENTIFIER()", syntaxError(39, "(")},
            {"CREATE SCHEMA cs_b14 CLONE IDENTIFIER(UPPER('x'))", syntaxError(37, "(")},
            {"CREATE TABLE cs_b15 CLONE IDENTIFIER(UPPER('x'))", syntaxError(36, "(")},
            {"CREATE STAGE cs_b16 CLONE IDENTIFIER()", syntaxError(36, "(")},
            {"CREATE TABLE cs_b17 CLONE IDENTIFIER('a' || 'b'); SELECT 1 +", syntaxError(36, "(")},
        });
    }
}
