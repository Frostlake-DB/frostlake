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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SHOW ORGANIZATION ACCOUNTS is the organization's administrator's: any other role is refused as an operation on the
 * account's own system, naming no privilege, with a LIKE as without one; the administrator reads SHOW ACCOUNTS's
 * columns and no row, as only the organization account lists any and this account is not it (live-verified).
 */
public class OrganizationAccountsRefusalTest extends MeasuredCellsTestSupport {

    /** The listing alone and with a LIKE. */
    @Test
    public void theListingIsRefusedOnAnAccountThatIsNotTheOrganizations() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"SHOW ORGANIZATION ACCOUNTS",
                "REFUSED SQL access control error: | Insufficient privileges to operate on  'SYSTEM'."},
            {"SHOW ORGANIZATION ACCOUNTS LIKE 'X%'",
                "REFUSED SQL access control error: | Insufficient privileges to operate on  'SYSTEM'."},
        });
    }

    /** To the organization's administrator, the listing answers the accounts' columns and no row. */
    @Test
    public void theOrganizationsAdministratorReadsNoAccountHere() {
        engine.execute("USE ROLE ORGADMIN");
        assertEquals(shape("SHOW ACCOUNTS"), shape("SHOW ORGANIZATION ACCOUNTS"));
        assertAnswer("", "SHOW ORGANIZATION ACCOUNTS");
    }
}
