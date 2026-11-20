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
 * Outbound shares are kept as they are declared: CREATE SHARE reads a comment, ALTER SHARE changes the comment and
 * the accounts the share reaches, DROP SHARE removes it, SHOW SHARES lists it as OUTBOUND and DESCRIBE SHARE answers
 * the objects granted to it - none. Embedded only: the statements are the documented ones and none of the answers
 * is measured against an account.
 */
public class ShareStatementsTest extends MeasuredCellsTestSupport {

    private static final String ACCOUNT_OBJECTS =
        "creates and drops shares, which the account this suite is checked against is not altered"
        + " by";

    @Override
    protected void setupTest() {
        Assumptions.assumeFalse(isLiveSnowflake(), ACCOUNT_OBJECTS);
    }

    /** A statement's rows with each moment masked. */
    private String listed(final String sql) {
        return answer(sql).replaceAll("\\d{4}-\\d{2}-\\d{2}T[0-9:.]+Z", "<TS>");
    }

    @Test
    public void aShareIsCreatedListedAlteredAndDropped() {
        assertAnswer("Share SH_A successfully created.", "CREATE SHARE sh_a COMMENT = 'one'");
        assertEquals("<TS>|OUTBOUND|ABCORG.ABC12345|SH_A|||SYSADMIN|one||",
            listed("SHOW SHARES LIKE 'SH_A'"));
        assertAnswer("Statement executed successfully.", "ALTER SHARE sh_a SET COMMENT = 'two'");
        assertAnswer("Statement executed successfully.", "ALTER SHARE sh_a ADD ACCOUNTS = org1.acc1, org1.acc2");
        assertEquals("<TS>|OUTBOUND|ABCORG.ABC12345|SH_A||ORG1.ACC1, ORG1.ACC2|SYSADMIN|two||",
            listed("SHOW SHARES LIKE 'SH_A'"));
        assertAnswer("Statement executed successfully.", "ALTER SHARE sh_a REMOVE ACCOUNTS = org1.acc1");
        assertAnswer("Statement executed successfully.", "ALTER SHARE sh_a UNSET COMMENT");
        assertEquals("<TS>|OUTBOUND|ABCORG.ABC12345|SH_A||ORG1.ACC2|SYSADMIN|||",
            listed("SHOW SHARES LIKE 'SH_A'"));
        assertAnswer("Statement executed successfully.", "ALTER SHARE sh_a SET ACCOUNTS = org2.acc9 COMMENT = 'c'");
        assertEquals("<TS>|OUTBOUND|ABCORG.ABC12345|SH_A||ORG2.ACC9|SYSADMIN|c||",
            listed("SHOW SHARES LIKE 'SH_A'"));
        assertEquals("", answer("DESCRIBE SHARE sh_a"));
        assertAnswer("SH_A successfully dropped.",
            "DROP SHARE sh_a");
        assertEquals("", listed("SHOW SHARES LIKE 'SH_A'"));
    }

    @Test
    public void aShareThatExistsOrIsMissingIsAnsweredAsOtherAccountObjects() {
        engine.execute("CREATE SHARE sh_b");
        assertAnswer("SH_B already exists, statement succeeded.", "CREATE SHARE IF NOT EXISTS sh_b COMMENT = 'x'");
        assertAnswer("REFUSED SQL compilation error: | Object 'SH_B' already exists.",
            "CREATE SHARE sh_b");
        assertAnswer("Share SH_B successfully created.", "CREATE OR REPLACE SHARE sh_b COMMENT = 'again'");
        assertAnswer("REFUSED SQL compilation error: | options IF NOT EXISTS and OR REPLACE are incompatible.",
            "CREATE OR REPLACE SHARE IF NOT EXISTS sh_b");
        engine.execute("DROP SHARE sh_b");
        assertAnswer("REFUSED SQL compilation error: | Share '\"<ACCOUNT>.SH_B\"' does not exist or not author"
            + "ized. Your primary role <ROLE> must have at least one privilege granted on SHARE \"<ACCO"
            + "UNT>.SH_B\".",
            "ALTER SHARE sh_b SET COMMENT = 'x'");
        assertAnswer("Statement executed successfully.", "ALTER SHARE IF EXISTS sh_b SET COMMENT = 'x'");
        assertAnswer("REFUSED SQL compilation error: | Share '\"<ACCOUNT>.SH_B\"' does not exist or not author"
            + "ized. Your primary role <ROLE> must have at least one privilege granted on SHARE \"<ACCO"
            + "UNT>.SH_B\".",
            "DROP SHARE sh_b");
        assertAnswer("Drop statement executed successfully (SH_B already dropped).",
            "DROP SHARE IF EXISTS sh_b");
        assertAnswer("REFUSED SQL compilation error: | Share '\"<ACCOUNT>.SH_B\"' does not exist or not author"
            + "ized. Your primary role <ROLE> must have at least one privilege granted on SHARE \"<ACCO"
            + "UNT>.SH_B\".",
            "DESCRIBE SHARE sh_b");
    }
}
