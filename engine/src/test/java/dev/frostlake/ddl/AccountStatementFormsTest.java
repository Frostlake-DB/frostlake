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
import dev.frostlake.storage.ResultSetColumn;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The organization administrator's account statements in their less common forms: DROP ACCOUNT without its grace
 * period, UNDROP ACCOUNT through IDENTIFIER(), SHOW TERSE ACCOUNTS, SHOW ORGANIZATION ACCOUNTS HISTORY, and ALTER
 * ACCOUNT's RENAME TO and comment for another account. ALTER ACCOUNT and CREATE ACCOUNT run embedded only: a live
 * test sends neither.
 */
public class AccountStatementFormsTest extends MeasuredCellsTestSupport {

    private static final String MISSING = "REFUSED SQL compilation error: | Account 'NOSUCH_ACCT_W7' does not exist "
        + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.";

    /** SHOW ACCOUNTS HISTORY's columns, which SHOW ORGANIZATION ACCOUNTS HISTORY reads too. */
    private static final List<String> HISTORY_COLUMNS = Arrays.asList("organization_name", "account_name",
        "snowflake_region", "edition", "account_url", "created_on", "comment", "account_locator", "account_locator_url",
        "managed_accounts", "consumption_billing_entity_name", "marketplace_consumer_billing_entity_name",
        "marketplace_provider_billing_entity_name", "old_account_url", "is_org_admin", "dropped_on",
        "scheduled_deletion_time", "restored_on", "account_old_url_saved_on", "account_old_url_last_used",
        "organization_old_url", "organization_old_url_saved_on", "organization_old_url_last_used",
        "moved_to_organization", "moved_on", "organization_URL_expiration_on", "is_events_account",
        "is_organization_account", "tenant_type", "domain_names", "contract_number");

    @Test
    public void aMissingAccountIsLookedForWithOrWithoutAGracePeriod() {
        engine.execute("USE ROLE ORGADMIN");
        assertAnswer(MISSING, "DROP ACCOUNT nosuch_acct_w7");
        assertAnswer(MISSING, "DROP ACCOUNT nosuch_acct_w7 GRACE_PERIOD_IN_DAYS = 3");
        assertAnswer("Drop statement executed successfully (NOSUCH_ACCT_W7 already dropped).",
            "DROP ACCOUNT IF EXISTS nosuch_acct_w7");
        assertAnswer("REFUSED SQL compilation error: | Account 'NOSUCH_ACCT_W7' does not exist or not authorized.",
            "UNDROP ACCOUNT IDENTIFIER('nosuch_acct_w7')");
    }

    @Test
    public void theAccountListingsTerseAndHistoryForms() {
        engine.execute("USE ROLE ORGADMIN");
        assertAnswer("REFUSED SQL compilation error: | Operation not available", "SHOW TERSE ACCOUNTS");
        assertAnswer("", "SHOW ORGANIZATION ACCOUNTS HISTORY");
        final List<String> names = new ArrayList<String>();
        for (final ResultSetColumn column : engine.executeQuery("SHOW ORGANIZATION ACCOUNTS HISTORY").getColumns()) {
            names.add(column.getName());
        }
        assertEquals(HISTORY_COLUMNS, names);
    }

    @Test
    public void anotherAccountIsRenamedOrItsCommentRefused() {
        if (isLiveSnowflake()) {
            return;
        }
        engine.execute("USE ROLE ORGADMIN");
        assertAnswer(MISSING, "ALTER ACCOUNT nosuch_acct_w7 RENAME TO other_w7");
        assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "ALTER ACCOUNT nosuch_acct_w7 SET COMMENT = 'x'");
        engine.execute("CREATE ACCOUNT acc_w7 ADMIN_NAME = a ADMIN_PASSWORD = 'p' EMAIL = 'e' EDITION = STANDARD");
        engine.execute("ALTER ACCOUNT acc_w7 RENAME TO acc_w8");
        assertEquals(1, engine.executeQuery("SHOW ACCOUNTS LIKE 'ACC_W8'").getRowCount());
        assertEquals(0, engine.executeQuery("SHOW ACCOUNTS LIKE 'ACC_W7'").getRowCount());
        // An account that is there wants the grace period the statement left out.
        assertAnswer("REFUSED SQL compilation error: | GRACE_PERIOD_IN_DAYS must be between 3 and 90.",
            "DROP ACCOUNT acc_w8");
        engine.execute("DROP ACCOUNT acc_w8 GRACE_PERIOD_IN_DAYS = 3");
        engine.execute("UNDROP ACCOUNT IDENTIFIER('acc_w8')");
        assertEquals(1, engine.executeQuery("SHOW ACCOUNTS LIKE 'ACC_W8'").getRowCount());
    }
}
