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
 * What the system roles hold from the account's start, live-verified: SYSADMIN, USERADMIN and SECURITYADMIN hold
 * their privileges on the account with the grant option, granted by no role; the role hierarchy's grants carry the
 * grant option and name no grantor; no role owns a system role, so SHOW ROLES and the INFORMATION_SCHEMA role views
 * give none and SHOW GRANTS ON a system role lists no OWNERSHIP. A scope's name refused for naming no database first,
 * and a FAILOVER or REPLICATION GROUP scope that is not there or lists no such kind, read as the account reads them.
 */
public class SystemRoleAccountGrantsTest extends MeasuredCellsTestSupport {

    @Test
    public void theSystemRolesGrantsAndOwners() {
        assertCells(new String[][] {
            {"SHOW GRANTS TO ROLE USERADMIN ->> SELECT LISTAGG(\"privilege\" || '|' || \"grant_option\" ||"
                + " '|' || \"granted_by\", ' + ') WITHIN GROUP (ORDER BY \"privilege\") FROM $1 WHERE "
                + "\"granted_on\" = 'ACCOUNT'",
                "CREATE ROLE|true| + CREATE USER|true|"},
            {"SHOW GRANTS TO ROLE SYSADMIN ->> SELECT LISTAGG(\"privilege\" || '|' || \"grant_option\" || "
                + "'|' || \"granted_by\", ' + ') WITHIN GROUP (ORDER BY \"privilege\") FROM $1 WHERE "
                + "\"granted_on\" = 'ACCOUNT'",
                "CREATE COMPUTE POOL|true| + CREATE DATABASE|true| + CREATE WAREHOUSE|true|"},
            {"SHOW GRANTS TO ROLE SECURITYADMIN ->> SELECT LISTAGG(\"privilege\" || '|' || "
                + "\"grant_option\" || '|' || \"granted_by\", ' + ') WITHIN GROUP (ORDER BY \"privilege\") FROM"
                + " $1 WHERE \"granted_on\" = 'ACCOUNT'",
                "APPLY AUTHENTICATION POLICY|true| + APPLY FEATURE POLICY|true| + APPLY INGRESS POLICY|true| "
                + "+ APPLY MAINTENANCE POLICY|true| + APPLY MULTI PARTY APPROVAL POLICY|true| + APPLY PACKAGES "
                + "POLICY|true| + APPLY PASSWORD POLICY|true| + APPLY SESSION POLICY|true| + ATTACH "
                + "POLICY|true| + BIND SERVICE ENDPOINT|true| + CREATE NETWORK POLICY|true| + MANAGE "
                + "APPLICATION SPECIFICATIONS|true| + MANAGE CALLER GRANTS|true| + MANAGE GRANTS|true| + MANAGE"
                + " ORGANIZATION ACCESS|true| + MANAGE SERVICE CALLER ACCESS|true| + MANAGE VISIBILITY|true|"},
            {"SHOW GRANTS ON ACCOUNT ->> SELECT COUNT(*) FROM $1 WHERE \"grantee_name\" IN ('SYSADMIN', "
                + "'USERADMIN', 'SECURITYADMIN') AND \"grant_option\" = 'true' AND \"granted_by\" = '' AND "
                + "\"granted_by_role_type\" = ''",
                "22"},
            {"SHOW GRANTS ON ROLE USERADMIN ->> SELECT LISTAGG(\"privilege\" || '|' || \"grantee_name\" ||"
                + " '|' || \"grant_option\" || '|' || \"granted_by\" || '|' || \"granted_by_role_type\", ' + ')"
                + " FROM $1",
                "USAGE|SECURITYADMIN|true||"},
            {"SHOW GRANTS ON ROLE SYSADMIN ->> SELECT LISTAGG(\"privilege\" || '|' || \"grantee_name\" || "
                + "'|' || \"grant_option\" || '|' || \"granted_by\" || '|' || \"granted_by_role_type\", ' + ') "
                + "FROM $1",
                "USAGE|ACCOUNTADMIN|true||"},
            {"SHOW GRANTS ON ROLE SECURITYADMIN ->> SELECT LISTAGG(\"privilege\" || '|' || "
                + "\"grantee_name\" || '|' || \"grant_option\" || '|' || \"granted_by\" || '|' || "
                + "\"granted_by_role_type\", ' + ') FROM $1",
                "USAGE|ACCOUNTADMIN|true||"},
            {"SHOW GRANTS ON ROLE ACCOUNTADMIN ->> SELECT COUNT(*) FROM $1",
                "0"},
            {"SHOW GRANTS ON ROLE PUBLIC ->> SELECT COUNT(*) FROM $1",
                "0"},
            {"SHOW GRANTS ON ROLE ORGADMIN ->> SELECT COUNT(*) FROM $1",
                "0"},
            {"SHOW GRANTS TO ROLE SECURITYADMIN ->> SELECT LISTAGG(\"privilege\" || '|' || \"name\" || '|'"
                + " || \"grant_option\" || '|' || \"granted_by\", ' + ') FROM $1 WHERE \"granted_on\" = 'ROLE'",
                "USAGE|USERADMIN|true|"},
            {"SHOW GRANTS TO ROLE ACCOUNTADMIN ->> SELECT LISTAGG(\"privilege\" || '|' || \"name\" || '|' "
                + "|| \"grant_option\" || '|' || \"granted_by\", ' + ') WITHIN GROUP (ORDER BY \"name\") FROM "
                + "$1 WHERE \"granted_on\" = 'ROLE' AND \"name\" IN ('SYSADMIN', 'SECURITYADMIN')",
                "USAGE|SECURITYADMIN|true| + USAGE|SYSADMIN|true|"},
            {"SHOW ROLES ->> SELECT LISTAGG(\"name\" || '|' || \"owner\", ' + ') WITHIN GROUP (ORDER BY "
                + "\"name\") FROM $1 WHERE \"name\" IN ('ACCOUNTADMIN', 'ORGADMIN', 'PUBLIC', 'SECURITYADMIN', "
                + "'SYSADMIN', 'USERADMIN')",
                "ACCOUNTADMIN| + ORGADMIN| + PUBLIC| + SECURITYADMIN| + SYSADMIN| + USERADMIN|"},
            {"SELECT COUNT(*) FROM INFORMATION_SCHEMA.ENABLED_ROLES WHERE ROLE_NAME IN ('ACCOUNTADMIN', "
                + "'ORGADMIN', 'PUBLIC', 'SECURITYADMIN', 'SYSADMIN', 'USERADMIN') AND ROLE_OWNER IS NOT NULL",
                "0"},
            {"SELECT COUNT(*) FROM INFORMATION_SCHEMA.APPLICABLE_ROLES WHERE ROLE_NAME IN ('ACCOUNTADMIN',"
                + " 'ORGADMIN', 'PUBLIC', 'SECURITYADMIN', 'SYSADMIN', 'USERADMIN') AND ROLE_OWNER IS NOT NULL",
                "0"},
            {"SHOW GRANTS OF ROLE SECURITYADMIN ->> SELECT LISTAGG(\"granted_to\" || '|' || "
                + "\"grantee_name\" || '|' || \"granted_by\", ' + ') FROM $1",
                "ROLE|ACCOUNTADMIN|"},
        });
    }

    @Test
    public void theScopesOfNoDatabaseOrGroup() {
        assertCells(new String[][] {
            {"SHOW TABLES IN CONNECTION prb_e2d_nosuch",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database "
                + "for PRB_E2D_NOSUCH"},
            {"SHOW VIEWS IN ORGANIZATION prb_e2d_nosuch",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database "
                + "for PRB_E2D_NOSUCH"},
            {"SHOW USERS IN ORGANIZATION prb_e2d_nosuch",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database "
                + "for PRB_E2D_NOSUCH"},
            {"SHOW DATABASES IN CONNECTION prb_e2d_nosuch",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database "
                + "for PRB_E2D_NOSUCH"},
            {"SHOW IMPORTED KEYS IN SCHEMA prb_e2d_nosuch",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database "
                + "for PRB_E2D_NOSUCH"},
            {"SHOW DATABASES IN FAILOVER GROUP prb_e2d_nosuch",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW DATABASES IN REPLICATION GROUP prb_e2d_nosuch",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW TABLES IN FAILOVER GROUP prb_e2d_nosuch",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "TABLE in FAILOVER GROUP'."},
            {"SHOW ROLES IN FAILOVER GROUP prb_e2d_nosuch",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "ROLE in FAILOVER GROUP'."},
            {"SHOW SCHEMAS IN REPLICATION GROUP prb_e2d_nosuch",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "SCHEMA in REPLICATION GROUP'."},
            {"SHOW EXPORTED KEYS IN SCHEMA prb_e2d_nosuch",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database "
                + "for PRB_E2D_NOSUCH"},
        });
    }
}
