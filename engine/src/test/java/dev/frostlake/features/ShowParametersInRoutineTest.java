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

package dev.frostlake.features;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * SHOW PARAMETERS IN FUNCTION, IN PROCEDURE and IN PIPE (live-verified): a routine is named with the argument types
 * of one overload and a pipe by its name; each lists its logging parameters with the value in force and the level
 * it comes from — the object's own, set by its ALTER, its schema's or its database's — and a name that reaches
 * nothing, a routine written without its argument list and a pipe written with one are each refused in the account's
 * own words.
 */
public class ShowParametersInRoutineTest extends MeasuredCellsTestSupport {

    /** A refusal's privilege hint reads alike whether or not the session carries secondary roles. */
    @Override
    protected String answer(final String sql) {
        return super.answer(sql).replace("<ROLE> or one of your secondary roles must", "<ROLE> must");
    }

    @Test
    public void namesAndRefusals() {
        engine.execute("CREATE FUNCTION f1(x FLOAT) RETURNS FLOAT AS '3.14::FLOAT * x'");
        engine.execute("CREATE PROCEDURE p1(x VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN x; END;'");
        engine.execute("CREATE TABLE t(id INT, name VARCHAR)");
        engine.execute("CREATE STAGE stg");
        engine.execute("CREATE PIPE pipe1 AS COPY INTO t FROM @stg");
        engine.execute("CREATE FUNCTION f0() RETURNS INT AS '1'");
        engine.execute("CREATE FUNCTION f1(x VARCHAR, y INT) RETURNS VARCHAR AS 'x'");
        assertAnswer("ENABLE_CONSOLE_OUTPUT|false|false||Enable stdout/stderr fast path logging for anonyous "
                + "stored procs. This is a public parameter (similar to LOG_LEVEL).|BOOLEAN ;; "
                + "LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering log events|STRING ;; "
                + "LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING ;; "
                + "METRIC_LEVEL|NONE|NONE||METRIC_LEVEL value to control whether to emit metrics to Event "
                + "Table|STRING ;; TRACE_LEVEL|OFF|OFF||Trace level value to use when generating/filtering "
                + "trace events|STRING",
            "SHOW PARAMETERS IN FUNCTION f1(FLOAT)");
        assertAnswer("ENABLE_CONSOLE_OUTPUT|false|false||Enable stdout/stderr fast path logging for anonyous "
                + "stored procs. This is a public parameter (similar to LOG_LEVEL).|BOOLEAN ;; "
                + "LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering log events|STRING ;; "
                + "LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING ;; "
                + "METRIC_LEVEL|NONE|NONE||METRIC_LEVEL value to control whether to emit metrics to Event "
                + "Table|STRING ;; TRACE_LEVEL|OFF|OFF||Trace level value to use when generating/filtering "
                + "trace events|STRING",
            "SHOW PARAMETERS IN FUNCTION TEST_DB.TEST_SCHEMA.f1(FLOAT)");
        assertAnswer("ENABLE_CONSOLE_OUTPUT|false|false||Enable stdout/stderr fast path logging for anonyous "
                + "stored procs. This is a public parameter (similar to LOG_LEVEL).|BOOLEAN ;; "
                + "LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering log events|STRING ;; "
                + "LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING ;; "
                + "METRIC_LEVEL|NONE|NONE||METRIC_LEVEL value to control whether to emit metrics to Event "
                + "Table|STRING ;; TRACE_LEVEL|OFF|OFF||Trace level value to use when generating/filtering "
                + "trace events|STRING",
            "SHOW PARAMETERS IN FUNCTION \"TEST_DB\".\"TEST_SCHEMA\".\"F1\"(FLOAT)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported statement type 'Cannot show parameters of "
                + "SqlIdentifier{qualifierNames=[], identifierName=FUNCTION}'.",
            "SHOW PARAMETERS IN FUNCTION f1");
        assertAnswer("REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.F1' does not exist or not"
                + " authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "FUNCTION TEST_DB.TEST_SCHEMA.F1.",
            "SHOW PARAMETERS IN FUNCTION f1()");
        assertAnswer("REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.F1' does not exist or not"
                + " authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "FUNCTION TEST_DB.TEST_SCHEMA.F1.",
            "SHOW PARAMETERS IN FUNCTION f1(VARCHAR)");
        assertAnswer("REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or"
                + " not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "FUNCTION TEST_DB.TEST_SCHEMA.NOSUCH.",
            "SHOW PARAMETERS IN FUNCTION nosuch(FLOAT)");
        assertAnswer("ENABLE_CONSOLE_OUTPUT|false|false||Enable stdout/stderr fast path logging for anonyous "
                + "stored procs. This is a public parameter (similar to LOG_LEVEL).|BOOLEAN ;; "
                + "LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering log events|STRING ;; "
                + "LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING ;; "
                + "METRIC_LEVEL|NONE|NONE||METRIC_LEVEL value to control whether to emit metrics to Event "
                + "Table|STRING ;; TRACE_LEVEL|OFF|OFF||Trace level value to use when generating/filtering "
                + "trace events|STRING",
            "SHOW PARAMETERS IN FUNCTION f1(DOUBLE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'FLOAT'.",
            "SHOW PARAMETERS IN FUNCTION f1(x FLOAT)");
        assertAnswer("ENABLE_CONSOLE_OUTPUT|false|false||Enable stdout/stderr fast path logging for anonyous "
                + "stored procs. This is a public parameter (similar to LOG_LEVEL).|BOOLEAN ;; "
                + "LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering log events|STRING ;; "
                + "LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING ;; "
                + "METRIC_LEVEL|NONE|NONE||METRIC_LEVEL value to control whether to emit metrics to Event "
                + "Table|STRING ;; TRACE_LEVEL|OFF|OFF||Trace level value to use when generating/filtering "
                + "trace events|STRING",
            "SHOW PARAMETERS IN FUNCTION f0()");
        assertAnswer("REFUSED SQL compilation error: | Unsupported statement type 'Cannot show parameters of "
                + "SqlIdentifier{qualifierNames=[], identifierName=FUNCTION}'.",
            "SHOW PARAMETERS IN FUNCTION f0");
        assertAnswer("REFUSED SQL compilation error: | Unsupported statement type 'No function name "
                + "specified'.",
            "SHOW PARAMETERS IN FUNCTION");
        assertAnswer("LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering log events|STRING ;; "
                + "LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING",
            "SHOW PARAMETERS LIKE '%LOG%' IN FUNCTION f1(FLOAT)");
        assertAnswer("ENABLE_CONSOLE_OUTPUT|false|false||Enable stdout/stderr fast path logging for anonyous "
                + "stored procs. This is a public parameter (similar to LOG_LEVEL).|BOOLEAN ;; "
                + "LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering log events|STRING ;; "
                + "LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING ;; "
                + "METRIC_LEVEL|NONE|NONE||METRIC_LEVEL value to control whether to emit metrics to Event "
                + "Table|STRING ;; TRACE_LEVEL|OFF|OFF||Trace level value to use when generating/filtering "
                + "trace events|STRING",
            "SHOW PARAMETERS FOR FUNCTION f1(FLOAT)");
        assertAnswer("ENABLE_CONSOLE_OUTPUT|false|false||Enable stdout/stderr fast path logging for anonyous "
                + "stored procs. This is a public parameter (similar to LOG_LEVEL).|BOOLEAN ;; "
                + "LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering log events|STRING ;; "
                + "LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING ;; "
                + "METRIC_LEVEL|NONE|NONE||METRIC_LEVEL value to control whether to emit metrics to Event "
                + "Table|STRING ;; TRACE_LEVEL|OFF|OFF||Trace level value to use when generating/filtering "
                + "trace events|STRING",
            "SHOW PARAMETERS IN FUNCTION f1(VARCHAR, INT)");
        assertAnswer("REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.F1' does not exist or not"
                + " authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "FUNCTION TEST_DB.TEST_SCHEMA.F1.",
            "SHOW PARAMETERS IN FUNCTION f1(VARCHAR(100), NUMBER(38,0))");
        assertAnswer("REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.P1' does not exist or not"
                + " authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "FUNCTION TEST_DB.TEST_SCHEMA.P1.",
            "SHOW PARAMETERS IN FUNCTION p1(VARCHAR)");
        assertAnswer("REFUSED SQL compilation error: | Database 'NODB' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NODB.",
            "SHOW PARAMETERS IN FUNCTION nodb.nosch.f1(FLOAT)");
        assertAnswer("REFUSED SQL compilation error: | Schema 'TEST_DB.NOSCH' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA TEST_DB.NOSCH.",
            "SHOW PARAMETERS IN FUNCTION nosch.f1(FLOAT)");
        assertAnswer("ENABLE_CONSOLE_OUTPUT|false|false||Enable stdout/stderr fast path logging for anonyous "
                + "stored procs. This is a public parameter (similar to LOG_LEVEL).|BOOLEAN ;; "
                + "LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering log events|STRING ;; "
                + "LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING ;; "
                + "METRIC_LEVEL|NONE|NONE||METRIC_LEVEL value to control whether to emit metrics to Event "
                + "Table|STRING ;; TRACE_LEVEL|OFF|OFF||Trace level value to use when generating/filtering "
                + "trace events|STRING",
            "SHOW PARAMETERS IN FUNCTION IDENTIFIER('f1')(FLOAT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'LIMIT'.",
            "SHOW PARAMETERS IN FUNCTION f1(FLOAT) LIMIT 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'BOGUS'.",
            "SHOW PARAMETERS IN FUNCTION f1(BOGUS)");
        assertAnswer("REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.F1' does not exist or not"
                + " authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "FUNCTION TEST_DB.TEST_SCHEMA.F1.",
            "SHOW PARAMETERS IN FUNCTION f1(FLOAT, FLOAT)");
        assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be "
                + "performed.",
            "SHOW PARAMETERS IN FUNCTION a.b.c.f1(FLOAT)");
        assertAnswer("REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.F1' does not exist or not"
                + " authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "FUNCTION TEST_DB.TEST_SCHEMA.F1.",
            "SHOW PARAMETERS IN FUNCTION f1(INT)");
        assertAnswer("AUTO_EVENT_LOGGING|OFF|OFF||Adds default logs and traces to stored procedures when "
                + "enabled|STRING ;; ENABLE_CONSOLE_OUTPUT|false|false||Enable stdout/stderr fast path "
                + "logging for anonyous stored procs. This is a public parameter (similar to "
                + "LOG_LEVEL).|BOOLEAN ;; LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering "
                + "log events|STRING ;; LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING ;;"
                + " METRIC_LEVEL|NONE|NONE||METRIC_LEVEL value to control whether to emit metrics to Event "
                + "Table|STRING ;; TRACE_LEVEL|OFF|OFF||Trace level value to use when generating/filtering "
                + "trace events|STRING",
            "SHOW PARAMETERS IN PROCEDURE p1(VARCHAR)");
        assertAnswer("AUTO_EVENT_LOGGING|OFF|OFF||Adds default logs and traces to stored procedures when "
                + "enabled|STRING ;; ENABLE_CONSOLE_OUTPUT|false|false||Enable stdout/stderr fast path "
                + "logging for anonyous stored procs. This is a public parameter (similar to "
                + "LOG_LEVEL).|BOOLEAN ;; LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering "
                + "log events|STRING ;; LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING ;;"
                + " METRIC_LEVEL|NONE|NONE||METRIC_LEVEL value to control whether to emit metrics to Event "
                + "Table|STRING ;; TRACE_LEVEL|OFF|OFF||Trace level value to use when generating/filtering "
                + "trace events|STRING",
            "SHOW PARAMETERS IN PROCEDURE \"TEST_DB\".\"TEST_SCHEMA\".\"P1\"(VARCHAR)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported statement type 'Cannot show parameters of "
                + "SqlIdentifier{qualifierNames=[], identifierName=PROCEDURE}'.",
            "SHOW PARAMETERS IN PROCEDURE p1");
        assertAnswer("REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.P1' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "PROCEDURE TEST_DB.TEST_SCHEMA.P1.",
            "SHOW PARAMETERS IN PROCEDURE p1()");
        assertAnswer("REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist "
                + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "PROCEDURE TEST_DB.TEST_SCHEMA.NOSUCH.",
            "SHOW PARAMETERS IN PROCEDURE nosuch(VARCHAR)");
        assertAnswer("REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.P1' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "PROCEDURE TEST_DB.TEST_SCHEMA.P1.",
            "SHOW PARAMETERS IN PROCEDURE p1(INT)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported statement type 'No function name "
                + "specified'.",
            "SHOW PARAMETERS IN PROCEDURE");
        assertAnswer("REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.F1' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "PROCEDURE TEST_DB.TEST_SCHEMA.F1.",
            "SHOW PARAMETERS IN PROCEDURE f1(FLOAT)");
        assertAnswer("LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING",
            "SHOW PARAMETERS LIKE 'LOG_LEVEL' IN PROCEDURE p1(VARCHAR)");
        assertAnswer("AUTO_EVENT_LOGGING|OFF|OFF||Adds default logs and traces to stored procedures when "
                + "enabled|STRING ;; ENABLE_CONSOLE_OUTPUT|false|false||Enable stdout/stderr fast path "
                + "logging for anonyous stored procs. This is a public parameter (similar to "
                + "LOG_LEVEL).|BOOLEAN ;; LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering "
                + "log events|STRING ;; LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING ;;"
                + " METRIC_LEVEL|NONE|NONE||METRIC_LEVEL value to control whether to emit metrics to Event "
                + "Table|STRING ;; TRACE_LEVEL|OFF|OFF||Trace level value to use when generating/filtering "
                + "trace events|STRING",
            "SHOW PARAMETERS IN PROCEDURE p1(STRING)");
        assertAnswer("REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.P1' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "PROCEDURE TEST_DB.TEST_SCHEMA.P1.",
            "SHOW PARAMETERS IN PROCEDURE p1(VARCHAR(5))");
        assertAnswer("AUTO_EVENT_LOGGING|OFF|OFF||Adds default logs and traces to stored procedures when "
                + "enabled|STRING ;; ENABLE_CONSOLE_OUTPUT|false|false||Enable stdout/stderr fast path "
                + "logging for anonyous stored procs. This is a public parameter (similar to "
                + "LOG_LEVEL).|BOOLEAN ;; LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering "
                + "log events|STRING ;; LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING ;;"
                + " METRIC_LEVEL|NONE|NONE||METRIC_LEVEL value to control whether to emit metrics to Event "
                + "Table|STRING ;; TRACE_LEVEL|OFF|OFF||Trace level value to use when generating/filtering "
                + "trace events|STRING",
            "SHOW PARAMETERS FOR PROCEDURE p1(VARCHAR)");
        assertAnswer("REFUSED SQL compilation error: | Schema 'TEST_DB.NOSCH' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA TEST_DB.NOSCH.",
            "SHOW PARAMETERS IN PROCEDURE nosch.p1(VARCHAR)");
        assertAnswer("REFUSED SQL compilation error: | Database 'NODB' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NODB.",
            "SHOW PARAMETERS IN PROCEDURE nodb.nosch.p1(VARCHAR)");
        assertAnswer("LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering log events|STRING ;; "
                + "LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING ;; "
                + "PIPE_EXECUTION_PAUSED|false|false||Pause processing of files for a pipe|BOOLEAN",
            "SHOW PARAMETERS IN PIPE pipe1");
        assertAnswer("LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering log events|STRING ;; "
                + "LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING ;; "
                + "PIPE_EXECUTION_PAUSED|false|false||Pause processing of files for a pipe|BOOLEAN",
            "SHOW PARAMETERS IN PIPE \"TEST_DB\".\"TEST_SCHEMA\".\"PIPE1\"");
        assertAnswer("REFUSED SQL compilation error: | Pipe 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not"
                + " authorized. Your primary role <ROLE> must have at least one privilege granted on PIPE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH.",
            "SHOW PARAMETERS IN PIPE nosuch");
        assertAnswer("REFUSED SQL compilation error: | This session does not have a current pipe",
            "SHOW PARAMETERS IN PIPE");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 24 | invalid identifier "
                + "'TOK_UDF_TARGET'",
            "SHOW PARAMETERS IN PIPE pipe1()");
        assertAnswer("",
            "SHOW PARAMETERS LIKE 'X%' IN PIPE pipe1");
        assertAnswer("REFUSED SQL compilation error: | Pipe 'TEST_DB.TEST_SCHEMA.T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PIPE "
                + "TEST_DB.TEST_SCHEMA.T.",
            "SHOW PARAMETERS IN PIPE t");
        assertAnswer("REFUSED SQL compilation error: | Schema 'TEST_DB.NOSCH' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA TEST_DB.NOSCH.",
            "SHOW PARAMETERS IN PIPE nosch.pipe1");
        assertAnswer("REFUSED SQL compilation error: | Database 'NODB' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NODB.",
            "SHOW PARAMETERS IN PIPE nodb.nosch.pipe1");
        assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be "
                + "performed.",
            "SHOW PARAMETERS IN PIPE a.b.c.pipe1");
        assertAnswer("LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering log events|STRING ;; "
                + "LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING ;; "
                + "PIPE_EXECUTION_PAUSED|false|false||Pause processing of files for a pipe|BOOLEAN",
            "SHOW PARAMETERS IN PIPE IDENTIFIER('pipe1')");
        assertAnswer("LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering log events|STRING ;; "
                + "LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING ;; "
                + "PIPE_EXECUTION_PAUSED|false|false||Pause processing of files for a pipe|BOOLEAN",
            "SHOW PARAMETERS FOR PIPE pipe1");
        engine.execute("ALTER FUNCTION f1(FLOAT) SET LOG_LEVEL = 'INFO'");
        assertAnswer("ENABLE_CONSOLE_OUTPUT|false|false||Enable stdout/stderr fast path logging for anonyous "
                + "stored procs. This is a public parameter (similar to LOG_LEVEL).|BOOLEAN ;; "
                + "LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering log events|STRING ;; "
                + "LOG_LEVEL|INFO|OFF|FUNCTION|LOG_LEVEL to use when filtering events|STRING ;; "
                + "METRIC_LEVEL|NONE|NONE||METRIC_LEVEL value to control whether to emit metrics to Event "
                + "Table|STRING ;; TRACE_LEVEL|OFF|OFF||Trace level value to use when generating/filtering "
                + "trace events|STRING",
            "SHOW PARAMETERS IN FUNCTION f1(FLOAT)");
        engine.execute("ALTER PROCEDURE p1(VARCHAR) SET TRACE_LEVEL = 'ALWAYS'");
        assertAnswer("AUTO_EVENT_LOGGING|OFF|OFF||Adds default logs and traces to stored procedures when "
                + "enabled|STRING ;; ENABLE_CONSOLE_OUTPUT|false|false||Enable stdout/stderr fast path "
                + "logging for anonyous stored procs. This is a public parameter (similar to "
                + "LOG_LEVEL).|BOOLEAN ;; LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering "
                + "log events|STRING ;; LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING ;;"
                + " METRIC_LEVEL|NONE|NONE||METRIC_LEVEL value to control whether to emit metrics to Event "
                + "Table|STRING ;; TRACE_LEVEL|ALWAYS|OFF|PROCEDURE|Trace level value to use when "
                + "generating/filtering trace events|STRING",
            "SHOW PARAMETERS IN PROCEDURE p1(VARCHAR)");
        engine.execute("ALTER SESSION SET LOG_LEVEL = 'WARN'");
        assertAnswer("ENABLE_CONSOLE_OUTPUT|false|false||Enable stdout/stderr fast path logging for anonyous "
                + "stored procs. This is a public parameter (similar to LOG_LEVEL).|BOOLEAN ;; "
                + "LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering log events|STRING ;; "
                + "LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING ;; "
                + "METRIC_LEVEL|NONE|NONE||METRIC_LEVEL value to control whether to emit metrics to Event "
                + "Table|STRING ;; TRACE_LEVEL|OFF|OFF||Trace level value to use when generating/filtering "
                + "trace events|STRING",
            "SHOW PARAMETERS IN FUNCTION f0()");
        assertAnswer("AUTO_EVENT_LOGGING|OFF|OFF||Adds default logs and traces to stored procedures when "
                + "enabled|STRING ;; ENABLE_CONSOLE_OUTPUT|false|false||Enable stdout/stderr fast path "
                + "logging for anonyous stored procs. This is a public parameter (similar to "
                + "LOG_LEVEL).|BOOLEAN ;; LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering "
                + "log events|STRING ;; LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING ;;"
                + " METRIC_LEVEL|NONE|NONE||METRIC_LEVEL value to control whether to emit metrics to Event "
                + "Table|STRING ;; TRACE_LEVEL|ALWAYS|OFF|PROCEDURE|Trace level value to use when "
                + "generating/filtering trace events|STRING",
            "SHOW PARAMETERS IN PROCEDURE p1(VARCHAR)");
        engine.execute("ALTER SCHEMA TEST_SCHEMA SET LOG_LEVEL = 'DEBUG'");
        assertAnswer("ENABLE_CONSOLE_OUTPUT|false|false||Enable stdout/stderr fast path logging for anonyous "
                + "stored procs. This is a public parameter (similar to LOG_LEVEL).|BOOLEAN ;; "
                + "LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering log events|STRING ;; "
                + "LOG_LEVEL|DEBUG|OFF|SCHEMA|LOG_LEVEL to use when filtering events|STRING ;; "
                + "METRIC_LEVEL|NONE|NONE||METRIC_LEVEL value to control whether to emit metrics to Event "
                + "Table|STRING ;; TRACE_LEVEL|OFF|OFF||Trace level value to use when generating/filtering "
                + "trace events|STRING",
            "SHOW PARAMETERS IN FUNCTION f0()");
        assertAnswer("ENABLE_CONSOLE_OUTPUT|false|false||Enable stdout/stderr fast path logging for anonyous "
                + "stored procs. This is a public parameter (similar to LOG_LEVEL).|BOOLEAN ;; "
                + "LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering log events|STRING ;; "
                + "LOG_LEVEL|INFO|OFF|FUNCTION|LOG_LEVEL to use when filtering events|STRING ;; "
                + "METRIC_LEVEL|NONE|NONE||METRIC_LEVEL value to control whether to emit metrics to Event "
                + "Table|STRING ;; TRACE_LEVEL|OFF|OFF||Trace level value to use when generating/filtering "
                + "trace events|STRING",
            "SHOW PARAMETERS IN FUNCTION f1(FLOAT)");
        engine.execute("ALTER FUNCTION f1(FLOAT) UNSET LOG_LEVEL");
        assertAnswer("ENABLE_CONSOLE_OUTPUT|false|false||Enable stdout/stderr fast path logging for anonyous "
                + "stored procs. This is a public parameter (similar to LOG_LEVEL).|BOOLEAN ;; "
                + "LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering log events|STRING ;; "
                + "LOG_LEVEL|DEBUG|OFF|SCHEMA|LOG_LEVEL to use when filtering events|STRING ;; "
                + "METRIC_LEVEL|NONE|NONE||METRIC_LEVEL value to control whether to emit metrics to Event "
                + "Table|STRING ;; TRACE_LEVEL|OFF|OFF||Trace level value to use when generating/filtering "
                + "trace events|STRING",
            "SHOW PARAMETERS IN FUNCTION f1(FLOAT)");
        assertAnswer("LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering log events|STRING ;; "
                + "LOG_LEVEL|DEBUG|OFF|SCHEMA|LOG_LEVEL to use when filtering events|STRING ;; "
                + "PIPE_EXECUTION_PAUSED|false|false||Pause processing of files for a pipe|BOOLEAN",
            "SHOW PARAMETERS IN PIPE pipe1");
        assertAnswer("LOG_EVENT_LEVEL|STRING ;; LOG_LEVEL|STRING ;; PIPE_EXECUTION_PAUSED|BOOLEAN",
            "SELECT \"key\", \"type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        engine.execute("ALTER SESSION UNSET LOG_LEVEL");
    }

    @Test
    public void settingsAndInheritance() {
        engine.execute("CREATE FUNCTION f1(x FLOAT) RETURNS FLOAT AS '3.14::FLOAT * x'");
        engine.execute("CREATE PROCEDURE p1(x VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN x; END;'");
        engine.execute("CREATE TABLE t(id INT, name VARCHAR)");
        engine.execute("CREATE STAGE stg");
        engine.execute("CREATE PIPE pipe1 AS COPY INTO t FROM @stg");
        engine.execute("ALTER FUNCTION f1(FLOAT) SET METRIC_LEVEL = 'ALL'");
        engine.execute("ALTER FUNCTION f1(FLOAT) SET ENABLE_CONSOLE_OUTPUT = true");
        engine.execute("ALTER FUNCTION f1(FLOAT) SET LOG_EVENT_LEVEL = 'ERROR'");
        assertAnswer("REFUSED SQL compilation error: | cannot set parameter 'AUTO_EVENT_LOGGING' at 'FUNCTION'",
            "ALTER FUNCTION f1(FLOAT) SET AUTO_EVENT_LOGGING = 'ALL'");
        engine.execute("ALTER FUNCTION f1(FLOAT) SET TRACE_LEVEL = 'ON_EVENT'");
        engine.execute("ALTER FUNCTION f1(FLOAT) SET LOG_LEVEL = 'warn'");
        assertAnswer("ENABLE_CONSOLE_OUTPUT|true|false|FUNCTION|Enable stdout/stderr fast path logging for "
                + "anonyous stored procs. This is a public parameter (similar to LOG_LEVEL).|BOOLEAN ;; "
                + "LOG_EVENT_LEVEL|ERROR|OFF|FUNCTION|LOG_EVENT_LEVEL to use when filtering log "
                + "events|STRING ;; LOG_LEVEL|warn|OFF|FUNCTION|LOG_LEVEL to use when filtering "
                + "events|STRING ;; METRIC_LEVEL|ALL|NONE|FUNCTION|METRIC_LEVEL value to control whether to"
                + " emit metrics to Event Table|STRING ;; TRACE_LEVEL|ON_EVENT|OFF|FUNCTION|Trace level "
                + "value to use when generating/filtering trace events|STRING",
            "SHOW PARAMETERS IN FUNCTION f1(FLOAT)");
        assertAnswer("REFUSED Invalid value for log level. Allowed values are "
                + "TRACE,DEBUG,INFO,WARN,ERROR,FATAL,OFF.",
            "ALTER FUNCTION f1(FLOAT) SET LOG_LEVEL = 'BOGUS'");
        assertAnswer("REFUSED SQL compilation error: | invalid value [5] for parameter 'LOG_LEVEL'",
            "ALTER FUNCTION f1(FLOAT) SET LOG_LEVEL = 5");
        assertAnswer("REFUSED Invalid value for trace level. Allowed values are ALWAYS,ON_EVENT,PROPAGATE,OFF.",
            "ALTER FUNCTION f1(FLOAT) SET TRACE_LEVEL = 'BOGUS'");
        engine.execute("ALTER FUNCTION f1(FLOAT) UNSET LOG_LEVEL, TRACE_LEVEL");
        engine.execute("ALTER FUNCTION f1(FLOAT) UNSET METRIC_LEVEL");
        engine.execute("ALTER FUNCTION f1(FLOAT) UNSET ENABLE_CONSOLE_OUTPUT");
        engine.execute("ALTER FUNCTION f1(FLOAT) UNSET LOG_EVENT_LEVEL");
        assertAnswer("ENABLE_CONSOLE_OUTPUT|false|false||Enable stdout/stderr fast path logging for anonyous "
                + "stored procs. This is a public parameter (similar to LOG_LEVEL).|BOOLEAN ;; "
                + "LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering log events|STRING ;; "
                + "LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING ;; "
                + "METRIC_LEVEL|NONE|NONE||METRIC_LEVEL value to control whether to emit metrics to Event "
                + "Table|STRING ;; TRACE_LEVEL|OFF|OFF||Trace level value to use when generating/filtering "
                + "trace events|STRING",
            "SHOW PARAMETERS IN FUNCTION f1(FLOAT)");
        engine.execute("ALTER PROCEDURE p1(VARCHAR) SET METRIC_LEVEL = 'ALL'");
        engine.execute("ALTER PROCEDURE p1(VARCHAR) SET ENABLE_CONSOLE_OUTPUT = true");
        engine.execute("ALTER PROCEDURE p1(VARCHAR) SET LOG_EVENT_LEVEL = 'ERROR'");
        engine.execute("ALTER PROCEDURE p1(VARCHAR) SET AUTO_EVENT_LOGGING = 'ALL'");
        assertAnswer("REFUSED SQL compilation error: | invalid value [BOGUS] for parameter "
                + "'AUTO_EVENT_LOGGING'",
            "ALTER PROCEDURE p1(VARCHAR) SET AUTO_EVENT_LOGGING = 'BOGUS'");
        engine.execute("ALTER PROCEDURE p1(VARCHAR) SET LOG_LEVEL = 'INFO' TRACE_LEVEL = 'ALWAYS'");
        assertAnswer("AUTO_EVENT_LOGGING|ALL|OFF|PROCEDURE|Adds default logs and traces to stored procedures "
                + "when enabled|STRING ;; ENABLE_CONSOLE_OUTPUT|true|false|PROCEDURE|Enable stdout/stderr "
                + "fast path logging for anonyous stored procs. This is a public parameter (similar to "
                + "LOG_LEVEL).|BOOLEAN ;; LOG_EVENT_LEVEL|ERROR|OFF|PROCEDURE|LOG_EVENT_LEVEL to use when "
                + "filtering log events|STRING ;; LOG_LEVEL|INFO|OFF|PROCEDURE|LOG_LEVEL to use when "
                + "filtering events|STRING ;; METRIC_LEVEL|ALL|NONE|PROCEDURE|METRIC_LEVEL value to control"
                + " whether to emit metrics to Event Table|STRING ;; TRACE_LEVEL|ALWAYS|OFF|PROCEDURE|Trace"
                + " level value to use when generating/filtering trace events|STRING",
            "SHOW PARAMETERS IN PROCEDURE p1(VARCHAR)");
        engine.execute("ALTER PROCEDURE p1(VARCHAR) UNSET AUTO_EVENT_LOGGING");
        engine.execute("ALTER PROCEDURE p1(VARCHAR) UNSET METRIC_LEVEL, ENABLE_CONSOLE_OUTPUT");
        assertAnswer("AUTO_EVENT_LOGGING|OFF|OFF||Adds default logs and traces to stored procedures when "
                + "enabled|STRING ;; ENABLE_CONSOLE_OUTPUT|false|false||Enable stdout/stderr fast path "
                + "logging for anonyous stored procs. This is a public parameter (similar to "
                + "LOG_LEVEL).|BOOLEAN ;; LOG_EVENT_LEVEL|ERROR|OFF|PROCEDURE|LOG_EVENT_LEVEL to use when "
                + "filtering log events|STRING ;; LOG_LEVEL|INFO|OFF|PROCEDURE|LOG_LEVEL to use when "
                + "filtering events|STRING ;; METRIC_LEVEL|NONE|NONE||METRIC_LEVEL value to control whether"
                + " to emit metrics to Event Table|STRING ;; TRACE_LEVEL|ALWAYS|OFF|PROCEDURE|Trace level "
                + "value to use when generating/filtering trace events|STRING",
            "SHOW PARAMETERS IN PROCEDURE p1(VARCHAR)");
        engine.execute("ALTER PIPE pipe1 SET PIPE_EXECUTION_PAUSED = true");
        assertAnswer("LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering log events|STRING ;; "
                + "LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING ;; "
                + "PIPE_EXECUTION_PAUSED|true|false|PIPE|Pause processing of files for a pipe|BOOLEAN",
            "SHOW PARAMETERS IN PIPE pipe1");
        engine.execute("ALTER PIPE pipe1 SET LOG_LEVEL = 'INFO'");
        engine.execute("ALTER PIPE pipe1 SET LOG_EVENT_LEVEL = 'INFO'");
        assertAnswer("LOG_EVENT_LEVEL|INFO|OFF|PIPE|LOG_EVENT_LEVEL to use when filtering log events|STRING ;;"
                + " LOG_LEVEL|INFO|OFF|PIPE|LOG_LEVEL to use when filtering events|STRING ;; "
                + "PIPE_EXECUTION_PAUSED|true|false|PIPE|Pause processing of files for a pipe|BOOLEAN",
            "SHOW PARAMETERS IN PIPE pipe1");
        engine.execute("ALTER PIPE pipe1 UNSET PIPE_EXECUTION_PAUSED");
        assertAnswer("LOG_EVENT_LEVEL|INFO|OFF|PIPE|LOG_EVENT_LEVEL to use when filtering log events|STRING ;;"
                + " LOG_LEVEL|INFO|OFF|PIPE|LOG_LEVEL to use when filtering events|STRING ;; "
                + "PIPE_EXECUTION_PAUSED|false|false||Pause processing of files for a pipe|BOOLEAN",
            "SHOW PARAMETERS IN PIPE pipe1");
        engine.execute("ALTER SCHEMA TEST_SCHEMA SET PIPE_EXECUTION_PAUSED = true");
        assertAnswer("LOG_EVENT_LEVEL|INFO|OFF|PIPE|LOG_EVENT_LEVEL to use when filtering log events|STRING ;;"
                + " LOG_LEVEL|INFO|OFF|PIPE|LOG_LEVEL to use when filtering events|STRING ;; "
                + "PIPE_EXECUTION_PAUSED|true|false|SCHEMA|Pause processing of files for a pipe|BOOLEAN",
            "SHOW PARAMETERS IN PIPE pipe1");
        engine.execute("ALTER SCHEMA TEST_SCHEMA UNSET PIPE_EXECUTION_PAUSED");
        engine.execute("ALTER PIPE pipe1 UNSET LOG_LEVEL");
        assertAnswer("LOG_EVENT_LEVEL|INFO|OFF|PIPE|LOG_EVENT_LEVEL to use when filtering log events|STRING ;;"
                + " LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING ;; "
                + "PIPE_EXECUTION_PAUSED|false|false||Pause processing of files for a pipe|BOOLEAN",
            "SHOW PARAMETERS IN PIPE pipe1");
        engine.execute("ALTER DATABASE TEST_DB SET LOG_LEVEL = 'ERROR' TRACE_LEVEL = 'ALWAYS'");
        assertAnswer("ENABLE_CONSOLE_OUTPUT|false|false||Enable stdout/stderr fast path logging for anonyous "
                + "stored procs. This is a public parameter (similar to LOG_LEVEL).|BOOLEAN ;; "
                + "LOG_EVENT_LEVEL|OFF|OFF||LOG_EVENT_LEVEL to use when filtering log events|STRING ;; "
                + "LOG_LEVEL|ERROR|OFF|DATABASE|LOG_LEVEL to use when filtering events|STRING ;; "
                + "METRIC_LEVEL|NONE|NONE||METRIC_LEVEL value to control whether to emit metrics to Event "
                + "Table|STRING ;; TRACE_LEVEL|ALWAYS|OFF|DATABASE|Trace level value to use when "
                + "generating/filtering trace events|STRING",
            "SHOW PARAMETERS IN FUNCTION f1(FLOAT)");
        assertAnswer("AUTO_EVENT_LOGGING|OFF|OFF||Adds default logs and traces to stored procedures when "
                + "enabled|STRING ;; ENABLE_CONSOLE_OUTPUT|false|false||Enable stdout/stderr fast path "
                + "logging for anonyous stored procs. This is a public parameter (similar to "
                + "LOG_LEVEL).|BOOLEAN ;; LOG_EVENT_LEVEL|ERROR|OFF|PROCEDURE|LOG_EVENT_LEVEL to use when "
                + "filtering log events|STRING ;; LOG_LEVEL|INFO|OFF|PROCEDURE|LOG_LEVEL to use when "
                + "filtering events|STRING ;; METRIC_LEVEL|NONE|NONE||METRIC_LEVEL value to control whether"
                + " to emit metrics to Event Table|STRING ;; TRACE_LEVEL|ALWAYS|OFF|PROCEDURE|Trace level "
                + "value to use when generating/filtering trace events|STRING",
            "SHOW PARAMETERS IN PROCEDURE p1(VARCHAR)");
        assertAnswer("LOG_EVENT_LEVEL|INFO|OFF|PIPE|LOG_EVENT_LEVEL to use when filtering log events|STRING ;;"
                + " LOG_LEVEL|ERROR|OFF|DATABASE|LOG_LEVEL to use when filtering events|STRING ;; "
                + "PIPE_EXECUTION_PAUSED|false|false||Pause processing of files for a pipe|BOOLEAN",
            "SHOW PARAMETERS IN PIPE pipe1");
        engine.execute("ALTER SCHEMA TEST_SCHEMA SET METRIC_LEVEL = 'ALL'");
        engine.execute("ALTER SCHEMA TEST_SCHEMA SET LOG_EVENT_LEVEL = 'WARN'");
        engine.execute("ALTER SCHEMA TEST_SCHEMA SET ENABLE_CONSOLE_OUTPUT = true");
        assertAnswer("ENABLE_CONSOLE_OUTPUT|true|false|SCHEMA|Enable stdout/stderr fast path logging for "
                + "anonyous stored procs. This is a public parameter (similar to LOG_LEVEL).|BOOLEAN ;; "
                + "LOG_EVENT_LEVEL|WARN|OFF|SCHEMA|LOG_EVENT_LEVEL to use when filtering log events|STRING "
                + ";; LOG_LEVEL|ERROR|OFF|DATABASE|LOG_LEVEL to use when filtering events|STRING ;; "
                + "METRIC_LEVEL|ALL|NONE|SCHEMA|METRIC_LEVEL value to control whether to emit metrics to "
                + "Event Table|STRING ;; TRACE_LEVEL|ALWAYS|OFF|DATABASE|Trace level value to use when "
                + "generating/filtering trace events|STRING",
            "SHOW PARAMETERS IN FUNCTION f1(FLOAT)");
        assertAnswer("AUTO_EVENT_LOGGING|OFF|OFF||Adds default logs and traces to stored procedures when "
                + "enabled|STRING ;; ENABLE_CONSOLE_OUTPUT|true|false|SCHEMA|Enable stdout/stderr fast path"
                + " logging for anonyous stored procs. This is a public parameter (similar to "
                + "LOG_LEVEL).|BOOLEAN ;; LOG_EVENT_LEVEL|ERROR|OFF|PROCEDURE|LOG_EVENT_LEVEL to use when "
                + "filtering log events|STRING ;; LOG_LEVEL|INFO|OFF|PROCEDURE|LOG_LEVEL to use when "
                + "filtering events|STRING ;; METRIC_LEVEL|ALL|NONE|SCHEMA|METRIC_LEVEL value to control "
                + "whether to emit metrics to Event Table|STRING ;; TRACE_LEVEL|ALWAYS|OFF|PROCEDURE|Trace "
                + "level value to use when generating/filtering trace events|STRING",
            "SHOW PARAMETERS IN PROCEDURE p1(VARCHAR)");
        assertAnswer("LOG_EVENT_LEVEL|INFO|OFF|PIPE|LOG_EVENT_LEVEL to use when filtering log events|STRING ;;"
                + " LOG_LEVEL|ERROR|OFF|DATABASE|LOG_LEVEL to use when filtering events|STRING ;; "
                + "PIPE_EXECUTION_PAUSED|false|false||Pause processing of files for a pipe|BOOLEAN",
            "SHOW PARAMETERS IN PIPE pipe1");
        assertAnswer("5",
            "SHOW PARAMETERS IN FUNCTION f1(FLOAT) ->> SELECT COUNT(*) FROM $1");
        assertAnswer("LOG_LEVEL|ERROR|OFF|DATABASE|LOG_LEVEL to use when filtering events|STRING",
            "SHOW PARAMETERS LIKE 'log_level' IN FUNCTION f1(FLOAT)");
        assertAnswer("ENABLE_CONSOLE_OUTPUT|true|false|SCHEMA|Enable stdout/stderr fast path logging for "
                + "anonyous stored procs. This is a public parameter (similar to LOG_LEVEL).|BOOLEAN ;; "
                + "LOG_EVENT_LEVEL|WARN|OFF|SCHEMA|LOG_EVENT_LEVEL to use when filtering log events|STRING "
                + ";; LOG_LEVEL|ERROR|OFF|DATABASE|LOG_LEVEL to use when filtering events|STRING ;; "
                + "METRIC_LEVEL|ALL|NONE|SCHEMA|METRIC_LEVEL value to control whether to emit metrics to "
                + "Event Table|STRING ;; TRACE_LEVEL|ALWAYS|OFF|DATABASE|Trace level value to use when "
                + "generating/filtering trace events|STRING",
            "SHOW PARAMETERS IN FUNCTION f1 (FLOAT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'STARTS'.",
            "SHOW PARAMETERS IN FUNCTION f1(FLOAT) STARTS WITH 'L'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'LIMIT'.",
            "SHOW PARAMETERS IN PIPE pipe1 LIMIT 1");
        assertAnswer("REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.F1' does not exist or not"
                + " authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "FUNCTION TEST_DB.TEST_SCHEMA.F1.",
            "SHOW PARAMETERS IN FUNCTION f1(ARRAY)");
        assertAnswer("ENABLE_CONSOLE_OUTPUT|true|false|SCHEMA|Enable stdout/stderr fast path logging for "
                + "anonyous stored procs. This is a public parameter (similar to LOG_LEVEL).|BOOLEAN ;; "
                + "LOG_EVENT_LEVEL|WARN|OFF|SCHEMA|LOG_EVENT_LEVEL to use when filtering log events|STRING "
                + ";; LOG_LEVEL|ERROR|OFF|DATABASE|LOG_LEVEL to use when filtering events|STRING ;; "
                + "METRIC_LEVEL|ALL|NONE|SCHEMA|METRIC_LEVEL value to control whether to emit metrics to "
                + "Event Table|STRING ;; TRACE_LEVEL|ALWAYS|OFF|DATABASE|Trace level value to use when "
                + "generating/filtering trace events|STRING",
            "SHOW PARAMETERS IN FUNCTION f1(FLOAT);");
        assertAnswer("AUTO_EVENT_LOGGING|OFF|OFF||Adds default logs and traces to stored procedures when "
                + "enabled|STRING ;; ENABLE_CONSOLE_OUTPUT|true|false|SCHEMA|Enable stdout/stderr fast path"
                + " logging for anonyous stored procs. This is a public parameter (similar to "
                + "LOG_LEVEL).|BOOLEAN ;; LOG_EVENT_LEVEL|ERROR|OFF|PROCEDURE|LOG_EVENT_LEVEL to use when "
                + "filtering log events|STRING ;; LOG_LEVEL|INFO|OFF|PROCEDURE|LOG_LEVEL to use when "
                + "filtering events|STRING ;; METRIC_LEVEL|ALL|NONE|SCHEMA|METRIC_LEVEL value to control "
                + "whether to emit metrics to Event Table|STRING ;; TRACE_LEVEL|ALWAYS|OFF|PROCEDURE|Trace "
                + "level value to use when generating/filtering trace events|STRING",
            "SHOW PARAMETERS IN PROCEDURE IDENTIFIER('p1')(VARCHAR)");
        assertAnswer("REFUSED SQL compilation error: | Schema 'TEST_DB.A' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.A.",
            "SHOW PARAMETERS IN FUNCTION a.b()");
        assertAnswer("REFUSED SQL compilation error: | Unsupported statement type 'Cannot show parameters of "
                + "SqlIdentifier{qualifierNames=[], identifierName=FUNCTION}'.",
            "SHOW PARAMETERS IN FUNCTION a.b");
        assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be "
                + "performed.",
            "SHOW PARAMETERS IN PIPE a.b.c.d.e");
        assertAnswer("REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.\"f1\"' does not exist or"
                + " not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "FUNCTION TEST_DB.TEST_SCHEMA.\"f1\".",
            "SHOW PARAMETERS IN FUNCTION \"f1\"(FLOAT)");
        assertAnswer("ENABLE_CONSOLE_OUTPUT|true|false|SCHEMA|Enable stdout/stderr fast path logging for "
                + "anonyous stored procs. This is a public parameter (similar to LOG_LEVEL).|BOOLEAN ;; "
                + "LOG_EVENT_LEVEL|WARN|OFF|SCHEMA|LOG_EVENT_LEVEL to use when filtering log events|STRING "
                + ";; LOG_LEVEL|ERROR|OFF|DATABASE|LOG_LEVEL to use when filtering events|STRING ;; "
                + "METRIC_LEVEL|ALL|NONE|SCHEMA|METRIC_LEVEL value to control whether to emit metrics to "
                + "Event Table|STRING ;; TRACE_LEVEL|ALWAYS|OFF|DATABASE|Trace level value to use when "
                + "generating/filtering trace events|STRING",
            "SHOW PARAMETERS IN FUNCTION f1(REAL)");
        assertAnswer("REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.F1' does not exist or not"
                + " authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "FUNCTION TEST_DB.TEST_SCHEMA.F1.",
            "SHOW PARAMETERS IN FUNCTION f1(NUMBER)");
        assertAnswer("AUTO_EVENT_LOGGING|OFF|OFF||Adds default logs and traces to stored procedures when "
                + "enabled|STRING ;; ENABLE_CONSOLE_OUTPUT|true|false|SCHEMA|Enable stdout/stderr fast path"
                + " logging for anonyous stored procs. This is a public parameter (similar to "
                + "LOG_LEVEL).|BOOLEAN ;; LOG_EVENT_LEVEL|ERROR|OFF|PROCEDURE|LOG_EVENT_LEVEL to use when "
                + "filtering log events|STRING ;; LOG_LEVEL|INFO|OFF|PROCEDURE|LOG_LEVEL to use when "
                + "filtering events|STRING ;; METRIC_LEVEL|ALL|NONE|SCHEMA|METRIC_LEVEL value to control "
                + "whether to emit metrics to Event Table|STRING ;; TRACE_LEVEL|ALWAYS|OFF|PROCEDURE|Trace "
                + "level value to use when generating/filtering trace events|STRING",
            "SHOW PARAMETERS IN PROCEDURE p1(TEXT)");
        assertAnswer("REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.P1' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "PROCEDURE TEST_DB.TEST_SCHEMA.P1.",
            "SHOW PARAMETERS IN PROCEDURE p1(CHAR)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ')'.",
            "SHOW PARAMETERS IN FUNCTION f1(FLOAT,)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'x'.",
            "SHOW PARAMETERS IN FUNCTION f1(FLOAT) x");
    }
}
