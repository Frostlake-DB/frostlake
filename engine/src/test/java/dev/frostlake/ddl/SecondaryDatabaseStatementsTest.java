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
 * CREATE DATABASE ... AS REPLICA OF makes a secondary database: an empty database that remembers its primary, which
 * SHOW REPLICATION DATABASES lists, WITH PRIMARY keeping one primary's secondaries. Nothing is refreshed into it.
 * Embedded only: the statement is the documented one and none of the answers is measured against an account, but
 * for where a word that is not REPLICA is refused (live-verified).
 */
public class SecondaryDatabaseStatementsTest extends MeasuredCellsTestSupport {

    private static final String ACCOUNT_OBJECTS =
        "creates a secondary database, which the account this suite is checked against is not"
        + " altered by";

    @Override
    protected void setupTest() {
        Assumptions.assumeFalse(isLiveSnowflake(), ACCOUNT_OBJECTS);
    }

    /** A statement's rows with each moment masked. */
    private String listed(final String sql) {
        return answer(sql).replaceAll("\\d{4}-\\d{2}-\\d{2}T[0-9:.]+Z", "<TS>");
    }

    @Test
    public void aSecondaryDatabaseIsListedWithItsPrimary() {
        engine.execute("CREATE DATABASE sd_a AS REPLICA OF org1.acc1.somedb COMMENT = 'one'");
        try {
            assertEquals("AWS_US_EAST_1|<TS>|ABC12345|SD_A|one|false|ORG1.ACC1.SOMEDB|||ABCORG|ABC12345",
            listed("SHOW REPLICATION DATABASES"));
            assertEquals("AWS_US_EAST_1|<TS>|ABC12345|SD_A|one|false|ORG1.ACC1.SOMEDB|||ABCORG|ABC12345",
            listed("SHOW REPLICATION DATABASES LIKE 'SD_A'"));
            assertEquals("AWS_US_EAST_1|<TS>|ABC12345|SD_A|one|false|ORG1.ACC1.SOMEDB|||ABCORG|ABC12345",
            listed("SHOW REPLICATION DATABASES WITH PRIMARY org1.acc1.somedb"));
            assertEquals("", listed("SHOW REPLICATION DATABASES WITH PRIMARY org1.acc1.other"));
            assertAnswer("Statement executed successfully.", "ALTER DATABASE sd_a SET COMMENT = 'two'");
            assertEquals(true, listed("SHOW DATABASES LIKE 'SD_A'").contains("|two|"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS sd_a");
            engine.execute("USE DATABASE test_db");
            engine.execute("USE SCHEMA test_schema");
        }
        assertEquals("", listed("SHOW REPLICATION DATABASES"));
    }

    @Test
    public void aWordThatIsNotReplicaIsRefusedWhereItStands() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'x'.",
            "CREATE DATABASE d1 AS REPLICA x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x'.",
            "CREATE DATABASE d1 AS x");
    }
}
