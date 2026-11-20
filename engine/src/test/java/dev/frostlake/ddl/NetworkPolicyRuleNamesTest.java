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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A network policy's rule lists name their rules as names, plain or qualified, or as strings, mixed freely
 * (live-verified). A plain name is read as the text it stands for and a qualified one as written, each then looked
 * up as the string would be; a rule a policy lists can be neither dropped nor replaced, and neither can its schema.
 */
public class NetworkPolicyRuleNamesTest extends MeasuredCellsTestSupport {

    private static final String POLICY = "NP_RULE_NAMES_T";

    private static final String NR1 = "[{\"fullyQualifiedRuleName\":\"TEST_DB.TEST_SCHEMA.NR1\"}]";

    private static final String NR2 = "[{\"fullyQualifiedRuleName\":\"TEST_DB.TEST_SCHEMA.NR2\"}]";

    private static final String COUNTS = "SHOW NETWORK POLICIES LIKE '" + POLICY + "' ->> SELECT \"name\" || '|' || "
        + "\"comment\" || '|' || \"entries_in_allowed_ip_list\" || '|' || \"entries_in_blocked_ip_list\" || '|' || "
        + "\"entries_in_allowed_network_rules\" || '|' || \"entries_in_blocked_network_rules\" FROM $1";

    @Override
    protected void setupTest() {
        engine.execute("DROP NETWORK POLICY IF EXISTS " + POLICY);
        engine.execute("CREATE NETWORK RULE nr1 TYPE = IPV4 MODE = INGRESS VALUE_LIST = ('192.168.0.100/24')");
        engine.execute("CREATE NETWORK RULE nr2 TYPE = IPV4 MODE = INGRESS VALUE_LIST = ('10.0.0.0/8')");
        engine.execute("CREATE NETWORK RULE \"nr low\" TYPE = IPV4 MODE = INGRESS VALUE_LIST = ('10.1.0.0/16')");
        engine.execute("CREATE NETWORK RULE \"NR UP\" TYPE = IPV4 MODE = INGRESS VALUE_LIST = ('10.2.0.0/16')");
        engine.execute("CREATE NETWORK RULE nre TYPE = HOST_PORT MODE = EGRESS VALUE_LIST = ('example.com:443')");
    }

    @Override
    protected void teardownTest() {
        engine.execute("DROP NETWORK POLICY IF EXISTS " + POLICY);
    }

    private String described(final String list) {
        return answer("DESCRIBE NETWORK POLICY " + POLICY + " ->> SELECT \"value\" FROM $1 WHERE \"name\" = '"
            + list + "'");
    }

    private void assertRefusalStarts(final String expected, final String sql) {
        final String answered = answer(sql);
        assertTrue(answered.startsWith("REFUSED " + expected), sql + " answered " + answered);
    }

    @Test
    public void aRuleListTakesQualifiedAndPlainNames() {
        engine.execute("CREATE NETWORK POLICY " + POLICY
            + " ALLOWED_NETWORK_RULE_LIST = (\"TEST_DB\".\"TEST_SCHEMA\".\"NR1\") COMMENT = 'one'");
        assertEquals(NR1, described("ALLOWED_NETWORK_RULE_LIST"));
        assertAnswer(POLICY + "|one|0|0|1|0", COUNTS);
        engine.execute("CREATE OR REPLACE NETWORK POLICY " + POLICY
            + " ALLOWED_NETWORK_RULE_LIST = (TEST_DB.TEST_SCHEMA.NR1, test_schema.nr2, nr1, 'nr2')"
            + " BLOCKED_NETWORK_RULE_LIST = (nr2) ALLOWED_IP_LIST = ('1.2.3.4')");
        assertEquals(NR2, described("BLOCKED_NETWORK_RULE_LIST"));
        assertAnswer(POLICY + "||1|0|2|1", COUNTS);
    }

    @Test
    public void aPlainNameIsTheTextItStandsFor() {
        engine.execute("CREATE NETWORK POLICY " + POLICY + " ALLOWED_NETWORK_RULE_LIST = (\"NR UP\")");
        assertEquals("[{\"fullyQualifiedRuleName\":\"TEST_DB.TEST_SCHEMA.NR UP\"}]",
            described("ALLOWED_NETWORK_RULE_LIST"));
        // The text nr low names NR LOW, as the string 'nr low' does; a qualified name keeps its quotes.
        final String missing = "SQL compilation error: | Network rule 'TEST_DB.TEST_SCHEMA.\"NR LOW\"' does not "
            + "exist or not authorized.";
        assertRefusalStarts(missing, "CREATE OR REPLACE NETWORK POLICY " + POLICY
            + " ALLOWED_NETWORK_RULE_LIST = (\"nr low\")");
        assertRefusalStarts(missing, "CREATE OR REPLACE NETWORK POLICY " + POLICY
            + " ALLOWED_NETWORK_RULE_LIST = ('nr low')");
        engine.execute("CREATE OR REPLACE NETWORK POLICY " + POLICY
            + " ALLOWED_NETWORK_RULE_LIST = (TEST_DB.TEST_SCHEMA.\"nr low\")");
        assertEquals("[{\"fullyQualifiedRuleName\":\"TEST_DB.TEST_SCHEMA.nr low\"}]",
            described("ALLOWED_NETWORK_RULE_LIST"));
        engine.execute("CREATE OR REPLACE NETWORK POLICY " + POLICY + " ALLOWED_NETWORK_RULE_LIST = (\"nr1\")");
        assertEquals(NR1, described("ALLOWED_NETWORK_RULE_LIST"));
        assertRefusalStarts("SQL compilation error: | Schema 'TEST_DB.\"test_schema\"' does not exist or not "
            + "authorized.", "CREATE OR REPLACE NETWORK POLICY " + POLICY
            + " ALLOWED_NETWORK_RULE_LIST = (\"test_schema\".nr1)");
    }

    @Test
    public void aRuleListRefusesWhatNamesNoRule() {
        engine.execute("CREATE NETWORK POLICY " + POLICY + " ALLOWED_NETWORK_RULE_LIST = (nr1)");
        final String replace = "CREATE OR REPLACE NETWORK POLICY " + POLICY + " ";
        assertRefusalStarts("SQL compilation error: | Network rule 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or "
            + "not authorized.", replace + "ALLOWED_NETWORK_RULE_LIST = (nosuch)");
        assertRefusalStarts("SQL compilation error: | Network rule 'TEST_DB.TEST_SCHEMA.NOSUCH1' does not exist or "
            + "not authorized.", replace + "ALLOWED_IP_LIST = ('x') BLOCKED_NETWORK_RULE_LIST = (nosuch1)");
        assertRefusalStarts("SQL compilation error: | Database 'NOSUCHDB' does not exist or not authorized.",
            replace + "ALLOWED_NETWORK_RULE_LIST = (NOSUCHDB.TEST_SCHEMA.NR1)");
        assertCells(new String[][] {
            {replace + "ALLOWED_NETWORK_RULE_LIST = (a.b.c.d)",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {replace + "ALLOWED_NETWORK_RULE_LIST = ('a.b.c.d')",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {replace + "ALLOWED_NETWORK_RULE_LIST = nr1",
                "REFUSED SQL compilation error: | invalid value [nr1] for parameter 'ALLOWED_NETWORK_RULE_LIST'"},
            {replace + "ALLOWED_NETWORK_RULE_LIST = 'nr1'",
                "REFUSED SQL compilation error: | invalid type of property ''nr1'' for 'ALLOWED_NETWORK_RULE_LIST'"},
            {replace + "ALLOWED_NETWORK_RULE_LIST = (nr1, 1)",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'ALLOWED_NETWORK_RULE_LIST'"},
            {replace + "ALLOWED_NETWORK_RULE_LIST = (nre)",
                "REFUSED The egress network rule NRE cannot be attached to the network policy " + POLICY
                    + ". Egress network rules do not take effect when attached to network policies."},
            {replace + "ALLOWED_NETWORK_RULE_LIST = (nr1,)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected ')'."},
            {replace + "ALLOWED_NETWORK_RULE_LIST = ('nr1') ALLOWED_NETWORK_RULE_LIST = (nr2)",
                "REFUSED SQL compilation error: | duplicate property 'ALLOWED_NETWORK_RULE_LIST';"},
        });
        assertEquals(NR1, described("ALLOWED_NETWORK_RULE_LIST"));
    }

    @Test
    public void aNameInAnotherListIsItsText() {
        final String create = "CREATE NETWORK POLICY " + POLICY + " ";
        assertCells(new String[][] {
            {create + "ALLOWED_IP_LIST = (nr1)", "REFUSED SQL compilation error: | Not supported IPv4 format for "
                + "values '[NR1]'.  Current support format is single IPv4 address with or without CIDR."},
            {create + "ALLOWED_IP_LIST = ('1.2.3.4', \"x y\")", "REFUSED SQL compilation error: | Not supported "
                + "IPv4 format for values '[x y]'.  Current support format is single IPv4 address with or without "
                + "CIDR."},
            {create + "ALLOWED_IP_LIST = (\"a\".\"b\")", "REFUSED SQL compilation error: | Not supported IPv4 "
                + "format for values '[\"a\".\"b\"]'.  Current support format is single IPv4 address with or "
                + "without CIDR."},
            {"CREATE NETWORK RULE nrx TYPE = IPV4 MODE = INGRESS VALUE_LIST = (a)",
                "REFUSED SQL compilation error: | invalid value '[A]' for property 'VALUE_LIST'"},
            {"CREATE NETWORK RULE nry TYPE = IPV4 MODE = INGRESS VALUE_LIST = (a.b)",
                "REFUSED SQL compilation error: | invalid value '[a.b]' for property 'VALUE_LIST'"},
            {"CREATE AUTHENTICATION POLICY ap2 CLIENT_TYPES = (a.b)",
                "REFUSED SQL Compilation Error: A.B invalid for Client Types. "},
            {"CREATE SESSION POLICY sp2 ALLOWED_SECONDARY_ROLES = (a.b)",
                "REFUSED SQL compilation error: | Role '[A.B]' does not exist or not authorized."},
            {"CREATE AUTHENTICATION POLICY ap3 SECURITY_INTEGRATIONS = (x)",
                "REFUSED SQL Compilation Error: Security integrations [X] do not exist or are inactive."},
        });
        engine.execute("CREATE AUTHENTICATION POLICY ap1 AUTHENTICATION_METHODS = (PASSWORD)");
        engine.execute("CREATE SESSION POLICY sp1 ALLOWED_SECONDARY_ROLES = (PUBLIC)");
        engine.execute("CREATE PACKAGES POLICY pk1 LANGUAGE PYTHON ALLOWLIST = (numpy)");
        assertAnswer("[PASSWORD]", "DESCRIBE AUTHENTICATION POLICY ap1 ->> SELECT \"value\" FROM $1 "
            + "WHERE \"property\" = 'AUTHENTICATION_METHODS'");
        assertAnswer("[PUBLIC]", "DESCRIBE SESSION POLICY sp1 ->> SELECT \"value\" FROM $1 "
            + "WHERE \"property\" = 'ALLOWED_SECONDARY_ROLES'");
    }

    @Test
    public void rulesAreSetAddedAndRemovedByName() {
        engine.execute("CREATE NETWORK POLICY " + POLICY + " BLOCKED_NETWORK_RULE_LIST = (nr2)");
        final String alter = "ALTER NETWORK POLICY " + POLICY + " ";
        engine.execute(alter + "SET ALLOWED_NETWORK_RULE_LIST = (TEST_DB.TEST_SCHEMA.NR1), "
            + "BLOCKED_NETWORK_RULE_LIST = () COMMENT = 'c'");
        assertEquals(NR1, described("ALLOWED_NETWORK_RULE_LIST"));
        assertAnswer(POLICY + "|c|0|0|1|0", COUNTS);
        engine.execute(alter + "ADD BLOCKED_NETWORK_RULE_LIST = (\"TEST_DB\".\"TEST_SCHEMA\".\"NR2\", nr2)");
        assertEquals(NR2, described("BLOCKED_NETWORK_RULE_LIST"));
        engine.execute(alter + "ADD ALLOWED_NETWORK_RULE_LIST = (nr2, 'nr1')");
        assertAnswer(POLICY + "|c|0|0|2|1", COUNTS);
        // Removing a rule the list does not hold changes nothing; one that does not exist is refused.
        engine.execute(alter + "REMOVE BLOCKED_NETWORK_RULE_LIST = (nr1)");
        engine.execute(alter + "REMOVE ALLOWED_NETWORK_RULE_LIST = (test_schema.nr2)");
        assertEquals(NR1, described("ALLOWED_NETWORK_RULE_LIST"));
        assertEquals(NR2, described("BLOCKED_NETWORK_RULE_LIST"));
        assertRefusalStarts("SQL compilation error: | Network rule 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or "
            + "not authorized.", alter + "REMOVE ALLOWED_NETWORK_RULE_LIST = (nosuch)");
        assertRefusalStarts("SQL compilation error: | Network rule 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or "
            + "not authorized.", alter + "ADD ALLOWED_NETWORK_RULE_LIST = (nosuch)");
        assertCells(new String[][] {
            {alter + "ADD ALLOWED_NETWORK_RULE_LIST = nr2",
                "REFUSED SQL compilation error: | invalid value [nr2] for parameter 'ALLOWED_NETWORK_RULE_LIST'"},
            {alter + "ADD ALLOWED_NETWORK_RULE_LIST = 'nr2'",
                "REFUSED SQL compilation error: | invalid type of property ''nr2'' for 'ALLOWED_NETWORK_RULE_LIST'"},
            {alter + "ADD ALLOWED_NETWORK_RULE_LIST = ('nr1', 1)",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'ALLOWED_NETWORK_RULE_LIST'"},
            {alter + "REMOVE ALLOWED_NETWORK_RULE_LIST = (1)",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'ALLOWED_NETWORK_RULE_LIST'"},
            {alter + "REMOVE ALLOWED_NETWORK_RULE_LIST = (a.b.c.d)",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {alter + "ADD ALLOWED_NETWORK_RULE_LIST = (nre)",
                "REFUSED The egress network rule NRE cannot be attached to the network policy " + POLICY
                    + ". Egress network rules do not take effect when attached to network policies."},
            {alter + "ADD ALLOWED_IP_LIST = ('1.1.1.1')",
                "REFUSED SQL compilation error: | invalid property 'ALLOWED_IP_LIST' for 'NETWORK_RULE'"},
            {alter + "ADD COMMENT = 'x'",
                "REFUSED SQL compilation error: | invalid property 'COMMENT' for 'NETWORK_RULE'"},
            {alter + "ADD NOSUCH = (nr1)",
                "REFUSED SQL compilation error: | invalid property 'NOSUCH' for 'NETWORK_POLICY'"},
        });
        engine.execute(alter + "UNSET ALLOWED_NETWORK_RULE_LIST");
        assertAnswer(POLICY + "|c|0|0|0|1", COUNTS);
    }

    @Test
    public void createOrAlterKeepsARuleListItLeavesOut() {
        engine.execute("CREATE NETWORK POLICY " + POLICY + " ALLOWED_NETWORK_RULE_LIST = (nr1) "
            + "BLOCKED_NETWORK_RULE_LIST = (nr2) ALLOWED_IP_LIST = ('1.2.3.4') COMMENT = 'c'");
        engine.execute("CREATE OR ALTER NETWORK POLICY " + POLICY + " BLOCKED_IP_LIST = ('5.6.7.8')");
        assertAnswer(POLICY + "||0|1|1|1", COUNTS);
        engine.execute("CREATE OR ALTER NETWORK POLICY " + POLICY + " ALLOWED_NETWORK_RULE_LIST = (nr2)");
        assertEquals(NR2, described("ALLOWED_NETWORK_RULE_LIST"));
        assertEquals(NR2, described("BLOCKED_NETWORK_RULE_LIST"));
        engine.execute("CREATE OR ALTER NETWORK POLICY " + POLICY
            + " ALLOWED_NETWORK_RULE_LIST = () BLOCKED_NETWORK_RULE_LIST = ()");
        assertAnswer(POLICY + "||0|0|0|0", COUNTS);
    }

    @Test
    public void aListedRuleAndItsSchemaStay() {
        engine.execute("CREATE SCHEMA s2");
        engine.execute("USE SCHEMA test_schema");
        engine.execute("CREATE NETWORK RULE s2.nr6 TYPE = IPV4 MODE = INGRESS VALUE_LIST = ('10.3.0.0/16')");
        engine.execute("CREATE NETWORK POLICY " + POLICY + " ALLOWED_NETWORK_RULE_LIST = (s2.nr6) "
            + "BLOCKED_NETWORK_RULE_LIST = (nr2)");
        assertCells(new String[][] {
            {"DROP NETWORK RULE nr2",
                "REFUSED Cannot drop Network rule NR2 as it is associated with some network policies."},
            {"DROP NETWORK RULE IF EXISTS s2.nr6",
                "REFUSED Cannot drop Network rule NR6 as it is associated with some network policies."},
            {"CREATE OR REPLACE NETWORK RULE nr2 TYPE = IPV4 MODE = INGRESS VALUE_LIST = ('10.0.0.0/8')",
                "REFUSED Cannot drop Network rule NR2 as it is associated with some network policies."},
            {"DROP SCHEMA s2",
                "REFUSED Cannot drop schema S2 as it includes network rule - policy associations."},
            {"DROP SCHEMA IF EXISTS s2",
                "REFUSED Cannot drop schema S2 as it includes network rule - policy associations."},
            {"CREATE OR REPLACE SCHEMA s2",
                "REFUSED Cannot drop schema S2 as it includes network rule - policy associations."},
        });
        engine.execute("DROP NETWORK RULE nr1");
        engine.execute("ALTER NETWORK POLICY " + POLICY + " UNSET ALLOWED_NETWORK_RULE_LIST");
        engine.execute("DROP SCHEMA s2");
        engine.execute("DROP NETWORK POLICY " + POLICY);
        engine.execute("DROP NETWORK RULE nr2");
    }
}
