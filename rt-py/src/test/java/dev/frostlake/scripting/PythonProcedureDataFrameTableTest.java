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

package dev.frostlake.scripting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.frostlake.query.MeasuredCellsTestSupport;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A Python procedure declared RETURNS TABLE that hands back a Snowpark DataFrame - from session.sql, from
 * session.create_dataframe, or a sorted session.table - answers the DataFrame's rows under its declared columns, and
 * one that catches a refused session.sql or session.table statement catches it as SnowparkSQLException. A handler
 * that hands back anything else, a DataFrame whose statement is refused or one whose columns do not fit is refused
 * (all live-verified). Each call runs under a time bound, so a call that never returns fails instead of stalling the
 * build.
 */
public class PythonProcedureDataFrameTableTest extends MeasuredCellsTestSupport {

    private static final Duration BOUND = Duration.ofSeconds(60);

    private static final String QID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    /** Asserts a statement's answer, its query IDs masked, within the time bound. */
    private void assertBoundedAnswer(final String expected, final String sql) {
        assertTimeoutPreemptively(BOUND, new Executable() {
            @Override
            public void execute() {
                assertEquals(expected, answer(sql).replaceAll(QID, "<QID>"), sql);
            }
        }, sql);
    }

    private void create(final String name, final String returns, final String body) {
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "() RETURNS " + returns + " LANGUAGE PYTHON"
            + " RUNTIME_VERSION = '3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\n"
            + body + "$$");
    }

    @Test
    public void aReturnedDataFrameIsTheCallsTable() {
        engine.execute("CREATE TABLE t(a INT, b VARCHAR)");
        engine.execute("INSERT INTO t VALUES (2, 'y'), (1, 'x')");
        create("pt1", "TABLE(x INT)", "def run(session):\n    return session.sql('SELECT 1 AS x')\n");
        create("pt2", "TABLE(a INT, b VARCHAR)",
            "def run(session):\n    return session.create_dataframe([[1, 'p'], [2, 'q']], schema=['a', 'b'])\n");
        create("pt3", "TABLE(a INT, b VARCHAR)", "def run(session):\n    return session.table('t').sort('a')\n");
        create("pt4", "TABLE()", "def run(session):\n    return session.sql('SELECT 7 AS v, 8 AS w')\n");

        assertBoundedAnswer("1", "CALL pt1()");
        assertBoundedAnswer("1|p ;; 2|q", "CALL pt2()");
        assertBoundedAnswer("1|x ;; 2|y", "CALL pt3()");
        assertBoundedAnswer("7|8", "CALL pt4()");
    }

    @Test
    public void whatIsNoDataFrameOrDoesNotFitIsRefused() {
        create("pf1", "TABLE(a INT)", "def run(session):\n    return session.table('nope')\n");
        create("pf2", "TABLE(a INT)", "def run(session):\n    return session.sql('SELECT 1/0 AS a')\n");
        create("pf3", "TABLE(a INT)", "def run(session):\n    return session.sql('SELECT 1 AS a WHERE 1 = 0')\n");
        create("pf4", "TABLE(a INT)", "def run(session):\n    return session.sql('SELECT 5 AS a').collect()\n");
        create("pg1", "TABLE(a INT)", "def run(session):\n    return None\n");
        create("pg2", "TABLE(a INT)", "def run(session):\n    return 'x'\n");
        create("pg3", "TABLE(a INT)", "def run(session):\n    return session.sql('SELECT 5 AS a, 6 AS b')\n");
        create("pg4", "TABLE(a INT, b INT)", "def run(session):\n    return session.sql('SELECT 5 AS a')\n");

        final String raised = "REFUSED Python Interpreter Error: | snowflake.snowpark.exceptions.SnowparkSQLException: "
            + "1304): <QID>: ";
        assertBoundedAnswer(raised + "002003 (42S02): SQL compilation error: | Object 'NOPE' does not exist or not "
            + "authorized. This executable runs with owner's rights. The owner role <ROLE> must have at least one "
            + "privilege granted on TABLE NOPE. in function PF1 with handler run", "CALL pf1()");
        assertBoundedAnswer(raised + "100051 (22012): Division by zero in function PF2 with handler run",
            "CALL pf2()");
        assertBoundedAnswer("", "CALL pf3()");
        final String noDataFrame = "REFUSED Return value should be a Snowpark dataframe object in function ";
        assertBoundedAnswer(noDataFrame + "PF4 with handler run", "CALL pf4()");
        assertBoundedAnswer("REFUSED NULL result in a non-nullable column of type TEXT in function PG1 with handler "
            + "run", "CALL pg1()");
        assertBoundedAnswer(noDataFrame + "PG2 with handler run", "CALL pg2()");
        final String mismatch = "REFUSED Stored procedure execution error: data type of returned table does not "
            + "match expected returned table type";
        assertBoundedAnswer(mismatch, "CALL pg3()");
        assertBoundedAnswer(mismatch, "CALL pg4()");
    }

    @Test
    public void aRefusedStatementIsCaughtAsSnowparksException() {
        create("pe2", "VARCHAR", """
            from snowflake.snowpark.exceptions import SnowparkSQLException
            def run(session):
                try:
                    session.sql("SELECT * FROM nope").collect()
                    return 'no'
                except SnowparkSQLException as e:
                    return 'caught ' + str(e.error_code) + ' ' + str(e.sfqid is not None)
            """);
        create("pe3", "VARCHAR", """
            from snowflake.snowpark.exceptions import SnowparkSQLException
            def run(session):
                try:
                    session.table('nope').collect()
                    return 'no'
                except SnowparkSQLException as e:
                    return 'caught'
            """);

        assertBoundedAnswer("caught 1304 True", "CALL pe2()");
        assertBoundedAnswer("caught", "CALL pe3()");
    }
}
