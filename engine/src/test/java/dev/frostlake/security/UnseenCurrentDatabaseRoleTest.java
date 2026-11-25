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

package dev.frostlake.security;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * What a session answers once its roles no longer see what it holds (live-verified, the roles in force being the
 * primary one alone): a current database the roles do not see is none - CURRENT_DATABASE() and CURRENT_SCHEMA()
 * answer NULL, a bare name finds nothing, and the session has it back once a role that sees it is in force again;
 * DESCRIBE ROLE describes no role, refusing a role that is there for want of MONITOR ROLE on the account, and as an
 * unsupported feature to the account's administrator; and an INFORMATION_SCHEMA table function of a database that is
 * not there, or not seen, is an invalid identifier spelled as written. Each test puts the session back on the role it
 * ran with, all secondary roles in force.
 */
public class UnseenCurrentDatabaseRoleTest extends MeasuredCellsTestSupport {

    private static final String NO_DATABASE = "This session does not have a current database. Call 'USE DATABASE', "
        + "or use a qualified name.";

    private static final String FAILED_USE = "REFUSED SQL compilation error: | Object does not exist, or operation "
        + "cannot be performed.";

    /** The role the session ran with before the test moved it, or null when it did not. */
    private String formerRole;

    /** Puts the session back on the role it ran with, all secondary roles in force. */
    @AfterEach
    public void restoreRole() {
        if (formerRole != null) {
            engine.execute("USE ROLE " + formerRole);
            engine.execute("USE SECONDARY ROLES ALL");
            formerRole = null;
        }
    }

    /** Moves the session to a role, with no secondary role in force, remembering the role it ran with. */
    private void useRoleAlone(final String role) {
        if (formerRole == null) {
            formerRole = answer("SELECT CURRENT_ROLE()");
        }
        engine.execute("USE ROLE " + role);
        engine.execute("USE SECONDARY ROLES NONE");
    }

    @Test
    public void aCurrentDatabaseTheRolesDoNotSeeIsNone() {
        actAsAccountAdministrator();
        engine.execute("CREATE OR REPLACE TABLE test_db.test_schema.t1 (a INT)");
        engine.execute("USE SCHEMA test_db.test_schema");
        assertAnswer("TEST_DB|TEST_SCHEMA", "SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()");
        useRoleAlone("PUBLIC");
        assertCells(new String[][] {
            {"SELECT CURRENT_ROLE(), CURRENT_DATABASE(), CURRENT_SCHEMA()", "PUBLIC|null|null"},
            {"SELECT CURRENT_SCHEMAS()", "[]"},
            {"USE SCHEMA test_schema", FAILED_USE},
            {"USE SCHEMA test_db.test_schema", FAILED_USE},
            {"USE DATABASE test_db", FAILED_USE},
            {"SELECT * FROM t1", "REFUSED SQL compilation error: | Object 'T1' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE T1."},
            {"CREATE TABLE t2 (a INT)", "REFUSED Cannot perform CREATE TABLE. " + NO_DATABASE},
            {"SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES", "REFUSED Cannot perform SELECT. " + NO_DATABASE},
            {"SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()", "null|null"},
        });
        engine.execute("USE SECONDARY ROLES ALL");
        assertAnswer("PUBLIC|TEST_DB|TEST_SCHEMA", "SELECT CURRENT_ROLE(), CURRENT_DATABASE(), CURRENT_SCHEMA()");
        engine.execute("USE SECONDARY ROLES NONE");
        assertAnswer("null|null", "SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()");
        engine.execute("USE ROLE " + formerRole);
        assertAnswer("TEST_DB|TEST_SCHEMA", "SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()");
    }

    @Test
    public void describeRoleDescribesNoRole() {
        actAsAccountAdministrator();
        final String[] roles = {"ACCOUNTADMIN", "PUBLIC", "SYSADMIN", "SECURITYADMIN", "USERADMIN"};
        for (final String as : new String[] {"PUBLIC", "SYSADMIN", "SECURITYADMIN", "USERADMIN"}) {
            useRoleAlone(as);
            for (final String role : roles) {
                assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on role '" + role
                    + "'. Your primary role <ROLE> must have MONITOR ROLE granted on ACCOUNT <ACCOUNT>.",
                    "DESCRIBE ROLE " + role);
            }
            assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on role 'PUBLIC'. "
                + "Your primary role <ROLE> must have MONITOR ROLE granted on ACCOUNT <ACCOUNT>.", "DESC ROLE public");
            assertAnswer("REFUSED SQL compilation error: | Role 'NO_SUCH_ROLE_X' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on ROLE NO_SUCH_ROLE_X.",
                "DESCRIBE ROLE no_such_role_x");
            assertAnswer("REFUSED SQL compilation error: | invalid parameter 'x'", "DESCRIBE ROLE PUBLIC x = 1");
            assertAnswer(FAILED_USE, "DESCRIBE ROLE a.b");
        }
        useRoleAlone("ACCOUNTADMIN");
        assertCells(new String[][] {
            {"DESCRIBE ROLE ACCOUNTADMIN", "REFUSED Unsupported feature 'ROLE'."},
            {"DESC ROLE PUBLIC;", "REFUSED Unsupported feature 'ROLE'."},
            {"DESCRIBE ROLE PUBLIC TYPE = STAGE", "REFUSED Unsupported feature 'ROLE'."},
            {"DESCRIBE ROLE PUBLIC()", "REFUSED Unsupported feature 'ROLE'."},
            {"DESCRIBE ROLE IDENTIFIER('PUBLIC')", "REFUSED Unsupported feature 'ROLE'."},
            {"DESCRIBE ROLE \"PUBLIC\"", "REFUSED Unsupported feature 'ROLE'."},
            {"DESCRIBE ROLE no_such_role_x", "REFUSED SQL compilation error: | Role 'NO_SUCH_ROLE_X' does not exist "
                + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on ROLE "
                + "NO_SUCH_ROLE_X."},
            {"DESCRIBE ROLE \"public\"", "REFUSED SQL compilation error: | Role '\"public\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on ROLE \"public\"."},
            {"DESCRIBE ROLE no_such_role_x x = 1", "REFUSED SQL compilation error: | invalid parameter 'x'"},
            {"DESCRIBE ROLE a.b.c", FAILED_USE},
            {"DESCRIBE ROLE", "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected "
                + "'<EOF>'."},
            {"DESCRIBE ROLE PUBLIC extra", "REFUSED SQL compilation error: | syntax error line 1 at position 26 "
                + "unexpected '<EOF>'."},
            {"DESCRIBE ROLE 'PUBLIC'", "REFUSED SQL compilation error: | syntax error line 1 at position 14 "
                + "unexpected ''PUBLIC''."},
            {"DESCRIBE ROLE PUBLIC, x = 1", "REFUSED SQL compilation error: | syntax error line 1 at position 20 "
                + "unexpected ','."},
            {"DESCRIBE ROLES PUBLIC", "REFUSED SQL compilation error: | syntax error line 1 at position 0 "
                + "unexpected 'DESCRIBE'."},
        });
    }

    @Test
    public void anInformationSchemaFunctionOfADatabaseNotThereIsAnInvalidIdentifier() {
        actAsAccountAdministrator();
        engine.execute("CREATE OR REPLACE TABLE test_db.test_schema.t1 (a INT)");
        engine.execute("USE SCHEMA test_db.test_schema");
        final String[] calls = {
            "TAG_REFERENCES('test_db.test_schema.t1', 'table')",
            "TAG_REFERENCES_ALL_COLUMNS('test_db.test_schema.t1', 'table')",
            "POLICY_REFERENCES(REF_ENTITY_NAME => 'test_db.test_schema.t1', REF_ENTITY_DOMAIN => 'table')",
            "DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => 'test_db.test_schema.t1', REF_ENTITY_DOMAIN => "
                + "'table')",
            "QUERY_HISTORY()", "QUERY_HISTORY_BY_USER()", "QUERY_HISTORY_BY_SESSION()", "TASK_HISTORY()",
            "SERVERLESS_TASK_HISTORY()", "CURRENT_TASK_GRAPHS()", "COMPLETE_TASK_GRAPHS()", "ALERT_HISTORY()",
            "DYNAMIC_TABLE_REFRESH_HISTORY()", "TASK_DEPENDENTS(TASK_NAME => 'test_db.test_schema.notask')",
            "COPY_HISTORY(TABLE_NAME => 'test_db.test_schema.t1', START_TIME => DATEADD(hour, -1, "
                + "CURRENT_TIMESTAMP()))",
            "TAG_REFERENCES('test_db.test_schema.t1')", "TAG_REFERENCES(1, 2, 3, 4)",
        };
        for (final String call : calls) {
            final String name = call.substring(0, call.indexOf('('));
            assertAnswer("REFUSED SQL compilation error: | Invalid identifier NOSUCHDB_X.INFORMATION_SCHEMA." + name,
                "SELECT * FROM TABLE(NOSUCHDB_X.INFORMATION_SCHEMA." + call + ")");
        }
        assertCells(new String[][] {
            {"SELECT * FROM TABLE(\"nosuchdb_x\".INFORMATION_SCHEMA.TAG_REFERENCES('test_db.test_schema.t1', "
                + "'table'))", "REFUSED SQL compilation error: | Invalid identifier "
                + "\"nosuchdb_x\".INFORMATION_SCHEMA.TAG_REFERENCES"},
            {"SELECT * FROM TABLE(nosuchdb_x.information_schema.\"TAG_REFERENCES\"('test_db.test_schema.t1', "
                + "'table'))", "REFUSED SQL compilation error: | Invalid identifier "
                + "NOSUCHDB_X.INFORMATION_SCHEMA.TAG_REFERENCES"},
            {"SELECT * FROM TABLE(test_db.test_schema.TAG_REFERENCES('test_db.test_schema.t1', 'table'))",
                "REFUSED SQL compilation error: | Invalid identifier TEST_DB.TEST_SCHEMA.TAG_REFERENCES"},
            {"SELECT * FROM TABLE(test_schema.TAG_REFERENCES('test_db.test_schema.t1', 'table'))",
                "REFUSED SQL compilation error: | Invalid identifier TEST_SCHEMA.TAG_REFERENCES"},
            {"SELECT * FROM TABLE(TAG_REFERENCES('test_db.test_schema.t1', 'table'))",
                "REFUSED SQL compilation error: | Invalid identifier TAG_REFERENCES"},
            {"SELECT * FROM TABLE(POLICY_REFERENCES(REF_ENTITY_NAME => 'test_db.test_schema.t1', "
                + "REF_ENTITY_DOMAIN => 'table'))", "REFUSED SQL compilation error: | Invalid identifier "
                + "POLICY_REFERENCES"},
            {"SELECT * FROM TABLE(test_schema.POLICY_REFERENCES(REF_ENTITY_NAME => 'test_db.test_schema.t1', "
                + "REF_ENTITY_DOMAIN => 'table'))", "REFUSED SQL compilation error: | Invalid identifier "
                + "TEST_SCHEMA.POLICY_REFERENCES"},
            {"SELECT * FROM TABLE(test_db.INFORMATION_SCHEMA.NO_SUCH_FN())", "REFUSED SQL compilation error: | "
                + "Unknown table function TEST_DB.INFORMATION_SCHEMA.NO_SUCH_FN"},
            {"SELECT * FROM TABLE(INFORMATION_SCHEMA.NO_SUCH_FN())", "REFUSED SQL compilation error: | "
                + "Unknown table function INFORMATION_SCHEMA.NO_SUCH_FN"},
            {"SELECT * FROM TABLE(NOSUCHDB_X.INFORMATION_SCHEMA.NO_SUCH_FN())", "REFUSED SQL compilation error: | "
                + "Unknown table function NOSUCHDB_X.INFORMATION_SCHEMA.NO_SUCH_FN"},
            {"SELECT * FROM TABLE(test_db.test_schema.NO_SUCH_FN())", "REFUSED SQL compilation error: | "
                + "Unknown user-defined table function TEST_DB.TEST_SCHEMA.NO_SUCH_FN"},
            {"SELECT * FROM TABLE(NOSUCHDB_X.test_schema.NO_SUCH_FN())", "REFUSED SQL compilation error: | "
                + "Unknown user-defined table function NOSUCHDB_X.TEST_SCHEMA.NO_SUCH_FN"},
            {"SELECT * FROM TABLE(NOSUCHDB_X.INFORMATION_SCHEMA.SPLIT_TO_TABLE('a', ','))", "1|1|a"},
            {"SELECT * FROM TABLE(test_db.INFORMATION_SCHEMA.TAG_REFERENCES('test_db.test_schema.t1', 'table'))",
                ""},
        });
        useRoleAlone("PUBLIC");
        for (final String call : calls) {
            final String name = call.substring(0, call.indexOf('('));
            assertAnswer("REFUSED SQL compilation error: | Invalid identifier TEST_DB.INFORMATION_SCHEMA." + name,
                "SELECT * FROM TABLE(test_db.INFORMATION_SCHEMA." + call + ")");
            assertAnswer("REFUSED SQL compilation error: | Invalid identifier INFORMATION_SCHEMA." + name,
                "SELECT * FROM TABLE(INFORMATION_SCHEMA." + call + ")");
        }
        assertAnswer("REFUSED SQL compilation error: | Invalid identifier NOSUCHDB_X.INFORMATION_SCHEMA.TAG_REFERENCES",
            "SELECT * FROM TABLE(NOSUCHDB_X.INFORMATION_SCHEMA.TAG_REFERENCES('test_db.test_schema.t1', 'table'))");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function TEST_DB.INFORMATION_SCHEMA.NO_SUCH_FN",
            "SELECT * FROM TABLE(test_db.INFORMATION_SCHEMA.NO_SUCH_FN())");
        assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
            + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
            "SELECT * FROM test_db.INFORMATION_SCHEMA.TABLES");
    }
}
