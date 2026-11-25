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
import org.junit.jupiter.api.Test;

/**
 * The SNOWFLAKE database and its schemas are owned by no role (live-verified): no role's SHOW GRANTS TO lists an
 * OWNERSHIP of them, SHOW GRANTS ON DATABASE SNOWFLAKE names no role at all, and neither SHOW DATABASES nor SHOW
 * SCHEMAS names a role as their owner. A database a role makes is still listed as that role's.
 */
public class SnowflakeDatabaseOwnershipTest extends MeasuredCellsTestSupport {

    /** Counts a role's OWNERSHIP rows on the SNOWFLAKE database and its schemas in the listing just run. */
    private static final String OWNED_SYSTEM_OBJECTS = """
        SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE "privilege" = 'OWNERSHIP'
        AND (("granted_on" = 'DATABASE' AND "name" = 'SNOWFLAKE')
        OR ("granted_on" = 'SCHEMA' AND "name" LIKE 'SNOWFLAKE.%'))""";

    @Test
    public void noRoleListsOwnershipOfTheSnowflakeDatabase() {
        actAsAccountAdministrator();
        for (final String role : new String[] {"SYSADMIN", "ACCOUNTADMIN", "SECURITYADMIN", "PUBLIC"}) {
            engine.execute("SHOW GRANTS TO ROLE " + role);
            assertAnswer("0", OWNED_SYSTEM_OBJECTS);
        }
    }

    @Test
    public void theSnowflakeDatabaseNamesNoOwner() {
        actAsAccountAdministrator();
        engine.execute("SHOW GRANTS ON DATABASE SNOWFLAKE");
        assertAnswer("0|0", """
            SELECT COUNT_IF("privilege" = 'OWNERSHIP'), COUNT_IF("granted_to" = 'ROLE')
            FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))""");
        engine.execute("SHOW DATABASES LIKE 'SNOWFLAKE'");
        assertAnswer("SNOWFLAKE||APPLICATION|", """
            SELECT "name", "owner", "kind", "owner_role_type" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))""");
        engine.execute("SHOW SCHEMAS IN DATABASE SNOWFLAKE");
        assertAnswer("0|0", """
            SELECT COUNT_IF("owner" NOT IN ('', 'SNOWFLAKE')), COUNT_IF("owner_role_type" = 'ROLE')
            FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))""");
    }

    @Test
    public void aDatabaseARoleMakesIsListedAsOwned() {
        actAsAccountAdministrator();
        engine.execute("SHOW GRANTS ON DATABASE test_db");
        assertAnswer("OWNERSHIP|DATABASE|ROLE|true|true", """
            SELECT "privilege", "granted_on", "granted_to", "grant_option", "granted_by" = "grantee_name"
            FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE "privilege" = 'OWNERSHIP'""");
    }
}
