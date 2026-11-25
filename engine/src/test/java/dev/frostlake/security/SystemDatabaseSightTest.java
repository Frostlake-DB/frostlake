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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The SNOWFLAKE database as a role below ACCOUNTADMIN sees it: fourteen of its schemas and the thirteen database
 * roles PUBLIC holds. To such a role a schema it does not see, ACCOUNT_USAGE among them, is a schema that does not
 * exist — a DESCRIBE, a read, a GET_DDL and a scoped listing refuse it as one, a USE and a SHOW over it fail as any
 * failed USE or SHOW does, and the account's and the database's listings leave it out — and so is a database role
 * it does not see. A secondary role reaching ACCOUNTADMIN sees the whole database, as ACCOUNTADMIN does. Every
 * answer is live-verified.
 */
public class SystemDatabaseSightTest extends MeasuredCellsTestSupport {

    /** The names of the last listing's rows, in order. */
    private static final String NAMES =
        "SELECT LISTAGG(\"name\", ',') WITHIN GROUP (ORDER BY \"name\") FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))";

    /** The role the session opened with, which each test gives back. */
    private String opener;

    @BeforeEach
    public void rememberTheRole() {
        opener = String.valueOf(engine.executeQuery("SELECT CURRENT_ROLE()").getRows().get(0).getValue(0));
    }

    @AfterEach
    public void restoreTheRole() {
        engine.execute("USE ROLE " + opener);
        engine.execute("USE SECONDARY ROLES NONE");
    }

    /**
     * The two listings below ACCOUNTADMIN, read by name: the account's role for the live session has no warehouse to
     * read a listing's result with, so the names are those of its rows as measured. PUBLIC is the engine's own default
     * working schema, which a real SNOWFLAKE database does not have.
     */
    @Test
    public void theListingsBelowAccountAdministrator() {
        assumeFalse(isLiveSnowflake(), "the live roles below ACCOUNTADMIN have no warehouse to read a result with");
        for (final String role : new String[] {"PUBLIC", "SYSADMIN"}) {
            engine.execute("USE ROLE " + role);
            engine.execute("SHOW SCHEMAS IN DATABASE SNOWFLAKE");
            assertAnswer("ALERT,CORE,CORTEX,DATA_PRIVACY,IMAGES,INFORMATION_SCHEMA,LOCAL,ML,MODELS,NOTIFICATION,PUBLIC,"
                + "SNOWPARK,SNOWPARK_CONNECT,SPCS,TAGS",
                NAMES);
            engine.execute("SHOW DATABASE ROLES IN DATABASE SNOWFLAKE");
            assertAnswer("ALERT_VIEWER,ANACONDA_REPOSITORY_USER,CLASSIFICATION_VIEWER,COPILOT_USER,CORE_VIEWER,"
                + "CORTEX_USER,DATA_METRIC_USER,DATA_PRIVACY_VIEWER,ML_USER,NOTIFICATION_VIEWER,PYPI_REPOSITORY_USER,"
                + "SPCS_REGISTRY_VIEWER,SPCS_VIEWER",
                NAMES);
        }
    }

    @Test
    public void asPublic() {
        engine.execute("USE SECONDARY ROLES NONE");
        engine.execute("USE ROLE PUBLIC");
        assertCells(new String[][] {
            {"SELECT CURRENT_ROLE()",
                "PUBLIC"},
            {"DESCRIBE SCHEMA SNOWFLAKE.ACCOUNT_USAGE",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.ACCOUNT_USAGE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.ACCOUNT_USAGE."},
            {"DESCRIBE SCHEMA \"SNOWFLAKE\".\"ACCOUNT_USAGE\"",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.ACCOUNT_USAGE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.ACCOUNT_USAGE."},
            {"DESCRIBE SCHEMA SNOWFLAKE.ORGANIZATION_USAGE",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.ORGANIZATION_USAGE' does not exist or not"
                + " authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.ORGANIZATION_USAGE."},
            {"DESCRIBE SCHEMA SNOWFLAKE.READER_ACCOUNT_USAGE",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.READER_ACCOUNT_USAGE' does not exist or "
                + "not authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.READER_ACCOUNT_USAGE."},
            {"SHOW VIEWS IN SCHEMA SNOWFLAKE.ACCOUNT_USAGE",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW TABLES IN SCHEMA SNOWFLAKE.ACCOUNT_USAGE",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW DYNAMIC TABLES IN SCHEMA SNOWFLAKE.ACCOUNT_USAGE",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW USER FUNCTIONS IN SCHEMA SNOWFLAKE.ACCOUNT_USAGE",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.ACCOUNT_USAGE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.ACCOUNT_USAGE."},
            {"USE SCHEMA SNOWFLAKE.ACCOUNT_USAGE",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SELECT 1 FROM SNOWFLAKE.ACCOUNT_USAGE.QUERY_HISTORY LIMIT 0",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.ACCOUNT_USAGE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.ACCOUNT_USAGE."},
            {"SELECT COUNT(*) FROM SNOWFLAKE.ACCOUNT_USAGE.QUERY_HISTORY",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.ACCOUNT_USAGE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.ACCOUNT_USAGE."},
            {"DESCRIBE VIEW SNOWFLAKE.ACCOUNT_USAGE.QUERY_HISTORY",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.ACCOUNT_USAGE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.ACCOUNT_USAGE."},
            {"SELECT GET_DDL('VIEW', 'SNOWFLAKE.ACCOUNT_USAGE.QUERY_HISTORY')",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.ACCOUNT_USAGE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.ACCOUNT_USAGE."},
            {"SELECT GET_DDL('SCHEMA', 'SNOWFLAKE.ACCOUNT_USAGE')",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.ACCOUNT_USAGE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.ACCOUNT_USAGE."},
            {"SHOW TERSE SCHEMAS LIKE 'ACCOUNT_USAGE' IN DATABASE SNOWFLAKE",
                ""},
            {"SHOW TERSE SCHEMAS LIKE 'ACCOUNT_USAGE' IN ACCOUNT",
                ""},
            {"SHOW TERSE VIEWS LIKE 'QUERY_HISTORY' IN DATABASE SNOWFLAKE",
                ""},
            {"SHOW TERSE VIEWS LIKE 'QUERY_HISTORY' IN ACCOUNT",
                ""},
            {"SHOW DATABASE ROLES LIKE 'USAGE_VIEWER' IN DATABASE SNOWFLAKE",
                ""},
            {"SHOW GRANTS TO DATABASE ROLE SNOWFLAKE.USAGE_VIEWER",
                "REFUSED SQL compilation error: | Database role 'SNOWFLAKE.USAGE_VIEWER' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on ROLE "
                + "SNOWFLAKE.USAGE_VIEWER."},
            {"SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.USAGE_VIEWER",
                "REFUSED SQL compilation error: | Database role 'SNOWFLAKE.USAGE_VIEWER' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on ROLE "
                + "SNOWFLAKE.USAGE_VIEWER."},
            {"DESCRIBE SCHEMA SNOWFLAKE.CORE",
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CORE'. "
                + "Provider share does not have sufficient privileges."},
            {"SELECT 1 FROM SNOWFLAKE.NO_SUCH_SCH.QUERY_HISTORY LIMIT 0",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.NO_SUCH_SCH' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.NO_SUCH_SCH."},
            {"DESCRIBE SCHEMA SNOWFLAKE.NO_SUCH_SCH",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.NO_SUCH_SCH' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.NO_SUCH_SCH."},
        });
    }

    @Test
    public void asSysadmin() {
        engine.execute("USE SECONDARY ROLES NONE");
        engine.execute("USE ROLE SYSADMIN");
        assertCells(new String[][] {
            {"SELECT CURRENT_ROLE()",
                "SYSADMIN"},
            {"DESCRIBE SCHEMA SNOWFLAKE.ACCOUNT_USAGE",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.ACCOUNT_USAGE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.ACCOUNT_USAGE."},
            {"DESCRIBE SCHEMA \"SNOWFLAKE\".\"ACCOUNT_USAGE\"",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.ACCOUNT_USAGE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.ACCOUNT_USAGE."},
            {"DESCRIBE SCHEMA SNOWFLAKE.ORGANIZATION_USAGE",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.ORGANIZATION_USAGE' does not exist or not"
                + " authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.ORGANIZATION_USAGE."},
            {"DESCRIBE SCHEMA SNOWFLAKE.READER_ACCOUNT_USAGE",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.READER_ACCOUNT_USAGE' does not exist or "
                + "not authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on"
                + " SCHEMA SNOWFLAKE.READER_ACCOUNT_USAGE."},
            {"SHOW VIEWS IN SCHEMA SNOWFLAKE.ACCOUNT_USAGE",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW TABLES IN SCHEMA SNOWFLAKE.ACCOUNT_USAGE",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW DYNAMIC TABLES IN SCHEMA SNOWFLAKE.ACCOUNT_USAGE",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW USER FUNCTIONS IN SCHEMA SNOWFLAKE.ACCOUNT_USAGE",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.ACCOUNT_USAGE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.ACCOUNT_USAGE."},
            {"USE SCHEMA SNOWFLAKE.ACCOUNT_USAGE",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SELECT 1 FROM SNOWFLAKE.ACCOUNT_USAGE.QUERY_HISTORY LIMIT 0",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.ACCOUNT_USAGE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.ACCOUNT_USAGE."},
            {"SELECT COUNT(*) FROM SNOWFLAKE.ACCOUNT_USAGE.QUERY_HISTORY",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.ACCOUNT_USAGE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.ACCOUNT_USAGE."},
            {"DESCRIBE VIEW SNOWFLAKE.ACCOUNT_USAGE.QUERY_HISTORY",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.ACCOUNT_USAGE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.ACCOUNT_USAGE."},
            {"SELECT GET_DDL('VIEW', 'SNOWFLAKE.ACCOUNT_USAGE.QUERY_HISTORY')",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.ACCOUNT_USAGE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.ACCOUNT_USAGE."},
            {"SELECT GET_DDL('SCHEMA', 'SNOWFLAKE.ACCOUNT_USAGE')",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.ACCOUNT_USAGE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.ACCOUNT_USAGE."},
            {"SHOW TERSE SCHEMAS LIKE 'ACCOUNT_USAGE' IN DATABASE SNOWFLAKE",
                ""},
            {"SHOW TERSE SCHEMAS LIKE 'ACCOUNT_USAGE' IN ACCOUNT",
                ""},
            {"SHOW TERSE VIEWS LIKE 'QUERY_HISTORY' IN DATABASE SNOWFLAKE",
                ""},
            {"SHOW TERSE VIEWS LIKE 'QUERY_HISTORY' IN ACCOUNT",
                ""},
            {"SHOW DATABASE ROLES LIKE 'USAGE_VIEWER' IN DATABASE SNOWFLAKE",
                ""},
            {"SHOW GRANTS TO DATABASE ROLE SNOWFLAKE.USAGE_VIEWER",
                "REFUSED SQL compilation error: | Database role 'SNOWFLAKE.USAGE_VIEWER' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on ROLE "
                + "SNOWFLAKE.USAGE_VIEWER."},
            {"SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.USAGE_VIEWER",
                "REFUSED SQL compilation error: | Database role 'SNOWFLAKE.USAGE_VIEWER' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on ROLE "
                + "SNOWFLAKE.USAGE_VIEWER."},
            {"DESCRIBE SCHEMA SNOWFLAKE.CORE",
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CORE'. "
                + "Provider share does not have sufficient privileges."},
            {"SELECT 1 FROM SNOWFLAKE.NO_SUCH_SCH.QUERY_HISTORY LIMIT 0",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.NO_SUCH_SCH' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.NO_SUCH_SCH."},
            {"DESCRIBE SCHEMA SNOWFLAKE.NO_SUCH_SCH",
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.NO_SUCH_SCH' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.NO_SUCH_SCH."},
        });
    }

    @Test
    public void aSecondaryRoleReachingAccountAdministratorSeesItAll() {
        engine.execute("USE ROLE PUBLIC");
        engine.execute("USE SECONDARY ROLES ALL");
        assertCells(new String[][] {
            {"DESCRIBE SCHEMA SNOWFLAKE.ACCOUNT_USAGE",
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema "
                + "'ACCOUNT_USAGE'. Provider share does not have sufficient privileges."},
            {"SHOW TERSE VIEWS LIKE 'QUERY_HISTORY' IN SCHEMA SNOWFLAKE.ACCOUNT_USAGE ->> SELECT \"name\" "
                + "FROM $1",
                "QUERY_HISTORY"},
            {"SELECT 1 FROM SNOWFLAKE.ACCOUNT_USAGE.QUERY_HISTORY LIMIT 0",
                ""},
            {"SHOW DATABASE ROLES LIKE 'USAGE_VIEWER' IN DATABASE SNOWFLAKE ->> SELECT \"name\" FROM $1",
                "USAGE_VIEWER"},
        });
    }

    @Test
    public void asAccountAdministrator() {
        engine.execute("USE SECONDARY ROLES NONE");
        engine.execute("USE ROLE ACCOUNTADMIN");
        assertCells(new String[][] {
            {"DESCRIBE SCHEMA SNOWFLAKE.ACCOUNT_USAGE",
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema "
                + "'ACCOUNT_USAGE'. Provider share does not have sufficient privileges."},
            {"SHOW TERSE VIEWS LIKE 'QUERY_HISTORY' IN SCHEMA SNOWFLAKE.ACCOUNT_USAGE ->> SELECT \"name\" "
                + "FROM $1",
                "QUERY_HISTORY"},
            {"SHOW TERSE SCHEMAS IN DATABASE SNOWFLAKE ->> SELECT COUNT(*) FROM $1 WHERE \"name\" IN "
                + "('ACCOUNT_USAGE', 'ORGANIZATION_USAGE', 'CORE')",
                "3"},
            {"SHOW DATABASE ROLES IN DATABASE SNOWFLAKE ->> SELECT COUNT(*) FROM $1",
                "43"},
        });
    }
}
