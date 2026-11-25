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
 * Replication groups and connections are kept as they are declared. A replication group is read as a failover group
 * is, shares the account's group names with the failover groups, is listed by SHOW REPLICATION GROUPS with its
 * REPLICATION type and not by SHOW FAILOVER GROUPS, and is altered and dropped by the statements that name its kind.
 * A connection keeps its comment, set by CREATE, ALTER and COMMENT ON, and the primary a secondary was declared a
 * replica of; SHOW CONNECTIONS lists it and DROP CONNECTION removes it. Nothing is replicated and no client is
 * redirected. Embedded only: the statements are the documented ones and are read as the account reads them, but
 * their answers create and alter objects of the account, which the account this suite is checked against is not.
 */
public class ReplicationGroupAndConnectionStatementsTest extends MeasuredCellsTestSupport {

    private static final String ACCOUNT_OBJECTS =
        "creates and drops replication groups and connections, which the account this suite is checked against is"
        + " not altered by";

    @Override
    protected void setupTest() {
        Assumptions.assumeFalse(isLiveSnowflake(), ACCOUNT_OBJECTS);
        engine.execute("CREATE DATABASE IF NOT EXISTS rg_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA test_schema");
    }

    /** A statement's rows with each moment masked. */
    private String listed(final String sql) {
        return answer(sql).replaceAll("\\d{4}-\\d{2}-\\d{2}T[0-9:.]+Z", "<TS>");
    }

    @Test
    public void aReplicationGroupIsCreatedListedAlteredAndDropped() {
        assertAnswer("Replication group RG_A successfully created.", "CREATE REPLICATION GROUP rg_a"
            + " OBJECT_TYPES = DATABASES ALLOWED_DATABASES = rg_db ALLOWED_ACCOUNTS = \"frostlake\".\"other\"");
        assertEquals("AWS_US_EAST_1|<TS>|ABC12345|RG_A|REPLICATION||true|ABCORG.ABC12345.RG_A|DATABASES||frostlak"
            + "e.other|ABCORG|ABC12345|null|null|null|SYSADMIN|false|null|false|false",
            listed("SHOW REPLICATION GROUPS"));
        assertEquals("", listed("SHOW FAILOVER GROUPS"));
        assertAnswer("Statement executed successfully.", "ALTER REPLICATION GROUP rg_a SET COMMENT = 'two'");
        assertEquals(true, listed("SHOW REPLICATION GROUPS LIKE 'RG_A'").contains("|REPLICATION|two|"));
        assertEquals(true, listed("SHOW DATABASES IN REPLICATION GROUP rg_a").startsWith("<TS>|RG_DB|"));
        assertAnswer("REFUSED SQL compilation error: | Object 'RG_A' already exists.",
            "CREATE FAILOVER GROUP rg_a OBJECT_TYPES = ROLES ALLOWED_ACCOUNTS = org1.acc1");
        assertAnswer("REFUSED SQL compilation error: | Replication group 'RG_A' does not exist or not authoriz"
            + "ed. Your primary role <ROLE> must have at least one privilege granted on REPLICATION GRO"
            + "UP RG_A.",
            "ALTER FAILOVER GROUP rg_a SET COMMENT = 'c'");
        assertAnswer("REFUSED SQL compilation error: | Replication group 'RG_A' does not exist or not authoriz"
            + "ed. Your primary role <ROLE> must have at least one privilege granted on REPLICATION GRO"
            + "UP RG_A.",
            "DROP FAILOVER GROUP rg_a");
        assertAnswer("REFUSED SQL compilation error: | invalid property 'FOO' for 'REPLICATION_GROUP'",
            "ALTER REPLICATION GROUP rg_a SET FOO = bar");
        assertAnswer("RG_A successfully dropped.", "DROP REPLICATION GROUP rg_a");
        assertEquals("", listed("SHOW REPLICATION GROUPS"));
    }

    @Test
    public void aSecondaryReplicationGroupNamesItsPrimary() {
        assertAnswer("Replication group RG_S successfully created.",
            "CREATE REPLICATION GROUP rg_s AS REPLICA OF \"frostlake\".\"other\".\"SOMERG\"");
        assertEquals("AWS_US_EAST_1|<TS>|ABC12345|RG_S|REPLICATION||false|frostlake.other.SOMERG||||ABCORG|ABC123"
            + "45|null|null|null|SYSADMIN|false|null|false|false",
            listed("SHOW REPLICATION GROUPS"));
        assertAnswer("RG_S successfully dropped.", "DROP REPLICATION GROUP IF EXISTS rg_s");
    }

    @Test
    public void aConnectionKeepsItsComment() {
        assertAnswer("Connection CON_A successfully created.", "CREATE CONNECTION con_a COMMENT = 'one'");
        assertEquals("AWS_US_EAST_1|<TS>|ABC12345|CON_A|one|ABCORG.ABC12345.CON_A|null|null|abcorg-con_a.snowfla"
            + "kecomputing.com",
            listed("SHOW CONNECTIONS LIKE 'CON_A'"));
        assertAnswer("CON_A already exists, statement succeeded.", "CREATE CONNECTION IF NOT EXISTS con_a");
        assertAnswer("REFUSED SQL compilation error: | Object 'CON_A' already exists.", "CREATE CONNECTION con_a");
        assertAnswer("Statement executed successfully.", "ALTER CONNECTION con_a SET COMMENT = 'two'");
        assertEquals(true, listed("SHOW CONNECTIONS").contains("|CON_A|two|"));
        assertAnswer("Statement executed successfully.", "ALTER CONNECTION con_a UNSET COMMENT");
        assertEquals(true, listed("SHOW CONNECTIONS").contains("|CON_A||"));
        assertAnswer("Statement executed successfully.", "COMMENT ON CONNECTION con_a IS 'three'");
        assertEquals(true, listed("SHOW CONNECTIONS").contains("|CON_A|three|"));
        assertAnswer("Statement executed successfully.",
            "ALTER CONNECTION con_a ENABLE FAILOVER TO ACCOUNTS org1.acc1, org1.acc2");
        assertAnswer("Statement executed successfully.", "ALTER CONNECTION con_a DISABLE FAILOVER");
        assertAnswer("CON_A successfully dropped.", "DROP CONNECTION con_a");
        assertEquals("", listed("SHOW CONNECTIONS"));
    }

    @Test
    public void aConnectionIsNamedByIdentifierAndRenamed() {
        assertAnswer("Connection CON_I successfully created.", "CREATE CONNECTION con_i");
        assertAnswer("Statement executed successfully.", "COMMENT ON CONNECTION IDENTIFIER('con_i') IS 'viaid'");
        assertEquals(true, listed("SHOW CONNECTIONS").contains("|CON_I|viaid|"));
        assertAnswer("Statement executed successfully.", "ALTER CONNECTION con_i SET COMMENT = abc");
        assertEquals(true, listed("SHOW CONNECTIONS").contains("|CON_I|ABC|"));
        assertAnswer("Statement executed successfully.", "ALTER CONNECTION con_i SET COMMENT = NULL");
        assertEquals(true, listed("SHOW CONNECTIONS").contains("|CON_I||"));
        assertAnswer("REFUSED SQL compilation error: | invalid property 'FOO' for 'CONNECTION'",
            "ALTER CONNECTION con_i SET FOO = 'x'");
        assertAnswer("Statement executed successfully.", "ALTER CONNECTION con_i RENAME TO con_j");
        assertEquals(true, listed("SHOW CONNECTIONS").contains("|CON_J|"));
        assertEquals(false, listed("SHOW CONNECTIONS").contains("|CON_I|"));
        assertAnswer("CON_J successfully dropped.", "DROP CONNECTION con_j");
    }

    @Test
    public void aSecondaryConnectionNamesItsPrimaryUntilPromoted() {
        assertAnswer("Connection CON_S successfully created.",
            "CREATE CONNECTION con_s AS REPLICA OF \"frostlake\".\"other\".\"SOMECON\" COMMENT = 'one'");
        assertEquals(true, listed("SHOW CONNECTIONS").contains("|CON_S|one|frostlake.other.SOMECON|"));
        assertAnswer("Statement executed successfully.", "ALTER CONNECTION con_s PRIMARY");
        assertEquals(true, listed("SHOW CONNECTIONS").contains("|CON_S|one|ABCORG.ABC12345.CON_S|"));
        assertAnswer("CON_S successfully dropped.", "DROP CONNECTION IF EXISTS con_s");
    }

    @Test
    public void aMissingConnectionIsRefused() {
        final String missing = "REFUSED SQL compilation error: | Connection 'NOPE' does not exist or not authorized. "
            + "Your primary role <ROLE> must have at least one privilege granted on CONNECTION NOPE.";
        assertAnswer(missing, "ALTER CONNECTION nope PRIMARY");
        assertAnswer(missing, "ALTER CONNECTION nope SET COMMENT = 'x'");
        assertAnswer(missing, "COMMENT ON CONNECTION nope IS 'x'");
        assertAnswer("Statement executed successfully.", "ALTER CONNECTION IF EXISTS nope PRIMARY");
        assertAnswer("Statement executed successfully.", "ALTER CONNECTION IF EXISTS nope UNSET COMMENT");
        assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "ALTER CONNECTION a.b SET COMMENT = 'x'");
    }
}
