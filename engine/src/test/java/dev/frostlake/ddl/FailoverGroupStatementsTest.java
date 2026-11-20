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
 * Failover groups are kept as they are declared: CREATE FAILOVER GROUP reads the object types, the allowed lists,
 * the accounts and the schedule, ALTER changes them, DROP removes the group, SHOW FAILOVER GROUPS lists it as this
 * account's primary, and SHOW DATABASES and SHOW SHARES IN FAILOVER GROUP list what the group allows. Nothing is
 * replicated. Embedded only: the statements are the documented ones, and of the answers only the two refusals of a
 * listing in a group that does not exist are live-verified.
 */
public class FailoverGroupStatementsTest extends MeasuredCellsTestSupport {

    private static final String ACCOUNT_OBJECTS =
        "creates and drops failover groups, which the account this suite is checked against is not"
        + " altered by";

    @Override
    protected void setupTest() {
        Assumptions.assumeFalse(isLiveSnowflake(), ACCOUNT_OBJECTS);
        engine.execute("CREATE DATABASE IF NOT EXISTS fg_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA test_schema");
    }

    /** A statement's rows with each moment masked. */
    private String listed(final String sql) {
        return answer(sql).replaceAll("\\d{4}-\\d{2}-\\d{2}T[0-9:.]+Z", "<TS>");
    }

    @Test
    public void aGroupIsCreatedListedAlteredAndDropped() {
        assertAnswer("Failover group FG_A successfully created.", "CREATE FAILOVER GROUP fg_a OBJECT_TYPES = DATABASES"
            + " ALLOWED_DATABASES = fg_db ALLOWED_ACCOUNTS = org1.acc1 REPLICATION_SCHEDULE = '10 MINUTE'");
        assertEquals("AWS_US_EAST_1|<TS>|ABC12345|FG_A|FAILOVER||true|ABCORG.ABC12345.FG_A|DATABASES||ORG1.ACC"
            + "1|ABCORG|ABC12345|10 MINUTE|null|null|SYSADMIN|false|null|false|false",
            listed("SHOW FAILOVER GROUPS"));
        assertEquals("AWS_US_EAST_1|<TS>|ABC12345|FG_A|FAILOVER||true|ABCORG.ABC12345.FG_A|DATABASES||ORG1.ACC"
            + "1|ABCORG|ABC12345|10 MINUTE|null|null|SYSADMIN|false|null|false|false",
            listed("SHOW REPLICATION GROUPS"));
        assertAnswer("Statement executed successfully.",
            "ALTER FAILOVER GROUP fg_a SET REPLICATION_SCHEDULE = '20 MINUTE'");
        assertAnswer("Statement executed successfully.", "ALTER FAILOVER GROUP fg_a UNSET OPTIMIZED_REFRESH");
        assertAnswer("Statement executed successfully.",
            "ALTER FAILOVER GROUP fg_a SET OBJECT_TYPES = DATABASES, ROLES, ACCOUNT PARAMETERS COMMENT = 'c'");
        assertAnswer("Statement executed successfully.",
            "ALTER FAILOVER GROUP fg_a ADD org1.acc2 TO ALLOWED_ACCOUNTS");
        assertEquals("AWS_US_EAST_1|<TS>|ABC12345|FG_A|FAILOVER|c|true|ABCORG.ABC12345.FG_A|DATABASES, ROLES, "
            + "ACCOUNT PARAMETERS||ORG1.ACC1, ORG1.ACC2|ABCORG|ABC12345|20 MINUTE|null|null|SYSADMIN|fa"
            + "lse|null|false|false",
            listed("SHOW FAILOVER GROUPS"));
        assertEquals(true, listed("SHOW DATABASES IN FAILOVER GROUP fg_a").startsWith("<TS>|FG_DB|"));
        assertEquals("", listed("SHOW SHARES IN FAILOVER GROUP fg_a"));
        assertAnswer("Statement executed successfully.",
            "ALTER FAILOVER GROUP fg_a REMOVE fg_db FROM ALLOWED_DATABASES");
        assertEquals("", listed("SHOW DATABASES IN FAILOVER GROUP fg_a"));
        assertAnswer("Statement executed successfully.", "ALTER FAILOVER GROUP fg_a RENAME TO fg_a2");
        assertAnswer("REFUSED SQL compilation error: | Replication group 'FG_A' does not exist or not authoriz"
            + "ed. Your primary role <ROLE> must have at least one privilege granted on REPLICATION GRO"
            + "UP FG_A.",
            "DROP FAILOVER GROUP fg_a");
        assertAnswer("FG_A2 successfully dropped.",
            "DROP FAILOVER GROUP fg_a2");
        assertEquals("", listed("SHOW FAILOVER GROUPS"));
    }

    @Test
    public void aSecondaryGroupNamesItsPrimary() {
        assertAnswer("Failover group FG_S successfully created.",
            "CREATE FAILOVER GROUP fg_s AS REPLICA OF org1.acc1.fg_p");
        assertEquals("AWS_US_EAST_1|<TS>|ABC12345|FG_S|FAILOVER||false|ORG1.ACC1.FG_P||||ABCORG|ABC12345|null|"
            + "null|null|SYSADMIN|false|null|false|false",
            listed("SHOW FAILOVER GROUPS"));
        assertAnswer("FG_S already exists, statement succeeded.",
            "CREATE FAILOVER GROUP IF NOT EXISTS fg_s AS REPLICA OF org1.acc1.fg_p");
        assertAnswer("Statement executed successfully.", "ALTER FAILOVER GROUP fg_s REFRESH");
        assertAnswer("FG_S successfully dropped.",
            "DROP FAILOVER GROUP IF EXISTS fg_s");
    }

    @Test
    public void whatIsMissingIsRefused() {
        assertAnswer("REFUSED SQL compilation error: | Missing option(s): [OBJECT_TYPES]",
            "CREATE FAILOVER GROUP fg_m ALLOWED_ACCOUNTS = org1.acc1");
        assertAnswer("REFUSED SQL compilation error: | Missing option(s): [ALLOWED_ACCOUNTS]",
            "CREATE FAILOVER GROUP fg_m OBJECT_TYPES = ROLES");
        assertAnswer("REFUSED SQL compilation error: | invalid property 'FOO' for 'FAILOVER_GROUP'",
            "CREATE FAILOVER GROUP fg_m OBJECT_TYPES = ROLES ALLOWED_ACCOUNTS = org1.acc1 FOO = bar");
        assertAnswer("REFUSED SQL compilation error: | Replication group 'FG_M' does not exist or not authoriz"
            + "ed. Your primary role <ROLE> must have at least one privilege granted on REPLICATION GRO"
            + "UP FG_M.",
            "ALTER FAILOVER GROUP fg_m SET COMMENT = 'c'");
        assertAnswer("Statement executed successfully.", "ALTER FAILOVER GROUP IF EXISTS fg_m SET COMMENT = 'c'");
        assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed"
            + ".",
            "SHOW DATABASES IN FAILOVER GROUP fg_m");
        assertAnswer("REFUSED SQL compilation error: | Replication group 'FG_M' does not exist or not authoriz"
            + "ed. Your primary role <ROLE> must have at least one privilege granted on REPLICATION GRO"
            + "UP FG_M.",
            "SHOW SHARES IN FAILOVER GROUP fg_m");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'x'.",
            "CREATE FAILOVER x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'GROUP'.",
            "CREATE FOO GROUP x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'GROUP'.",
            "ALTER FOO GROUP x SET y = 1");
    }
}
