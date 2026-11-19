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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Resource monitors are kept as they are declared: CREATE reads the quota, the frequency, the start and the end,
 * the users notified and the triggers, ALTER changes them, DROP removes the monitor and SHOW RESOURCE MONITORS
 * lists what was declared, with no credit ever used. Embedded only: the statements are the documented ones and
 * none of the answers is measured against an account.
 */
public class ResourceMonitorStatementsTest extends MeasuredCellsTestSupport {

    private static final String ACCOUNT_OBJECTS =
        "creates and drops resource monitors, which the account this suite is checked against is"
        + " not altered by";

    @Override
    protected void setupTest() {
        Assumptions.assumeFalse(isLiveSnowflake(), ACCOUNT_OBJECTS);
    }

    /** A statement's rows with each moment masked. */
    private String listed(final String sql) {
        return answer(sql).replaceAll("\\d{4}-\\d{2}-\\d{2}T[0-9:.]+Z", "<TS>");
    }

    @Test
    public void aMonitorIsCreatedListedAlteredAndDropped() {
        assertAnswer("Resource monitor RM_A successfully created.",
            "CREATE RESOURCE MONITOR rm_a WITH CREDIT_QUOTA = 100"
            + " FREQUENCY = MONTHLY START_TIMESTAMP = '2030-12-07 00:00' TRIGGERS ON 40 PERCENT DO NOTIFY"
            + " ON 50 PERCENT DO NOTIFY ON 80 PERCENT DO SUSPEND ON 90 PERCENT DO SUSPEND_IMMEDIATE");
        assertEquals("RM_A|100.00|0.00|100.00|null|MONTHLY|<TS>|null|40%,50%|80%|90%|<TS>|SYSADMIN||",
            listed("SHOW RESOURCE MONITORS LIKE 'RM_A'"));
        assertAnswer("Statement executed successfully.", "ALTER RESOURCE MONITOR rm_a SET CREDIT_QUOTA = 200");
        assertAnswer("Statement executed successfully.",
            "ALTER RESOURCE MONITOR rm_a SET END_TIMESTAMP = '2031-01-01' TRIGGERS ON 75 PERCENT DO NOTIFY");
        assertEquals("RM_A|200.00|0.00|200.00|null|MONTHLY|<TS>|<TS>|75%|null|null|<TS>|SYSADMIN||",
            listed("SHOW RESOURCE MONITORS"));
        assertAnswer("Statement executed successfully.",
            "ALTER RESOURCE MONITOR rm_a SET CREDIT_QUOTA = NULL END_TIMESTAMP = NULL NOTIFY_USERS = ()");
        assertEquals("RM_A|null|0.00|null|null|MONTHLY|<TS>|null|75%|null|null|<TS>|SYSADMIN||",
            listed("SHOW RESOURCE MONITORS"));
        assertAnswer("RM_A successfully dropped.",
            "DROP RESOURCE MONITOR rm_a");
        assertEquals("", listed("SHOW RESOURCE MONITORS"));
    }

    @Test
    public void aMonitorWithNoPropertyHasNoQuota() {
        assertAnswer("Resource monitor RM_B successfully created.", "CREATE RESOURCE MONITOR rm_b");
        assertEquals("RM_B|null|0.00|null|null|MONTHLY|<TS>|null|null|null|null|<TS>|SYSADMIN||",
            listed("SHOW RESOURCE MONITORS"));
        assertAnswer("RM_B already exists, statement succeeded.", "CREATE RESOURCE MONITOR IF NOT EXISTS rm_b");
        assertAnswer("REFUSED SQL compilation error: | Object 'RM_B' already exists.",
            "CREATE RESOURCE MONITOR rm_b");
        assertAnswer("Resource monitor RM_B successfully created.",
            "CREATE OR REPLACE RESOURCE MONITOR rm_b WITH CREDIT_QUOTA = 1.5 START_TIMESTAMP = IMMEDIATELY"
            + " FREQUENCY = NEVER");
        assertAnswer("RM_B successfully dropped.",
            "DROP RESOURCE MONITOR IF EXISTS rm_b");
        assertAnswer("Drop statement executed successfully (RM_B already dropped).",
            "DROP RESOURCE MONITOR IF EXISTS rm_b");
    }

    @Test
    public void whatIsNoPropertyAndNoTriggerIsRefused() {
        assertAnswer("REFUSED SQL compilation error: | invalid property 'FOO' for 'RESOURCE_MONITOR'",
            "CREATE RESOURCE MONITOR rm_c WITH FOO = 1");
        assertAnswer("REFUSED SQL compilation error: | invalid value 'HOURLY' for property 'FREQUENCY'",
            "CREATE RESOURCE MONITOR rm_c WITH FREQUENCY = HOURLY");
        assertAnswer("REFUSED SQL compilation error: | invalid value ''many'' for property 'CREDIT_QUOTA'",
            "CREATE RESOURCE MONITOR rm_c WITH CREDIT_QUOTA = 'many'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'PERCENTS"
            + "'.",
            "CREATE RESOURCE MONITOR rm_c WITH TRIGGERS ON 5 PERCENTS DO NOTIFY");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'RESUME'.",
            "CREATE RESOURCE MONITOR rm_c WITH TRIGGERS ON 5 PERCENT DO RESUME");
        assertAnswer("REFUSED SQL compilation error: | User 'NO_SUCH_USER_RM' does not exist or not authorized"
            + ".",
            "CREATE RESOURCE MONITOR rm_c WITH NOTIFY_USERS = (no_such_user_rm)");
        assertAnswer("REFUSED SQL compilation error: | Resource monitor 'RM_C' does not exist or not authorize"
            + "d. Your primary role <ROLE> must have at least one privilege granted on RESOURCE MONITOR"
            + " RM_C.",
            "ALTER RESOURCE MONITOR rm_c SET CREDIT_QUOTA = 1");
        assertAnswer("Statement executed successfully.", "ALTER RESOURCE MONITOR IF EXISTS rm_c SET CREDIT_QUOTA = 1");
        assertAnswer("REFUSED SQL compilation error: | Resource monitor 'RM_C' does not exist or not authorize"
            + "d. Your primary role <ROLE> must have at least one privilege granted on RESOURCE MONITOR"
            + " RM_C.",
            "DROP RESOURCE MONITOR rm_c");
        assertAnswer("REFUSED SQL compilation error: | options IF NOT EXISTS and OR REPLACE are incompatible.",
            "CREATE OR REPLACE RESOURCE MONITOR IF NOT EXISTS rm_c");
    }
}
