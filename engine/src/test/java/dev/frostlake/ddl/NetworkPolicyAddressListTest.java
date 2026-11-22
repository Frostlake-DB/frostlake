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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The addresses a network policy's ALLOWED_IP_LIST and BLOCKED_IP_LIST take (live-verified): each is an IPv4 address
 * with or without a CIDR prefix, read leniently, and the ones of a list that are not are refused together. A policy
 * keeps its addresses trimmed and without a slash at their end, a comma inside an item separating several.
 */
public class NetworkPolicyAddressListTest extends MeasuredCellsTestSupport {

    private static final String POLICY = "FL_ADDRESS_LIST_NP";

    private static final String SET_ALLOWED = "ALTER NETWORK POLICY " + POLICY + " SET ALLOWED_IP_LIST = ";

    @BeforeEach
    public void createPolicy() {
        engine.execute("DROP NETWORK POLICY IF EXISTS " + POLICY);
        engine.execute("CREATE NETWORK POLICY " + POLICY + " ALLOWED_IP_LIST = ('1.2.3.4')");
    }

    @AfterEach
    public void dropPolicy() {
        engine.execute("DROP NETWORK POLICY IF EXISTS " + POLICY);
    }

    private static String refused(final String values) {
        return "REFUSED SQL compilation error: | Not supported IPv4 format for values '" + values
            + "'.  Current support format is single IPv4 address with or without CIDR.";
    }

    private void assertTaken(final String sql) {
        final String answer = answer(sql);
        assertFalse(answer.startsWith("REFUSED"), sql + " => " + answer);
    }

    @Test
    public void anEntryThatIsNoAddressIsRefusedByItsValue() {
        assertCells(new String[][] {
            {SET_ALLOWED + "('abc')", refused("[abc]")},
            {"ALTER NETWORK POLICY " + POLICY + " SET BLOCKED_IP_LIST = ('abc')", refused("[abc]")},
            {SET_ALLOWED + "('::1')", refused("[::1]")},
            {SET_ALLOWED + "('2001:db8::/32')", refused("[2001:db8::/32]")},
            {SET_ALLOWED + "('1.2.3.256')", refused("[1.2.3.256]")},
            {SET_ALLOWED + "('256.1.1.1')", refused("[256.1.1.1]")},
            {SET_ALLOWED + "('1.2.3.4/33')", refused("[1.2.3.4/33]")},
            {SET_ALLOWED + "('1.2.3.4/0033')", refused("[1.2.3.4/0033]")},
            {SET_ALLOWED + "('1.2.3.4/a')", refused("[1.2.3.4/a]")},
            {SET_ALLOWED + "('1.2.3.4/-1')", refused("[1.2.3.4/-1]")},
            {SET_ALLOWED + "('1.2.3.4/+')", refused("[1.2.3.4/+]")},
            {SET_ALLOWED + "('1.2.3.4/8/9')", refused("[1.2.3.4/8/9]")},
            {SET_ALLOWED + "('1.2.3')", refused("[1.2.3]")},
            {SET_ALLOWED + "('1.2.3.')", refused("[1.2.3.]")},
            {SET_ALLOWED + "('1.2.3.4.5')", refused("[1.2.3.4.5]")},
            {SET_ALLOWED + "('1.2.3.4..')", refused("[1.2.3.4..]")},
            {SET_ALLOWED + "('.1.2.3.4')", refused("[.1.2.3.4]")},
            {SET_ALLOWED + "('1..2.3')", refused("[1..2.3]")},
            {SET_ALLOWED + "('1. 2.3.4')", refused("[1. 2.3.4]")},
            {SET_ALLOWED + "('1.2.3.0x4')", refused("[1.2.3.0x4]")},
            {SET_ALLOWED + "('1.2.3.99999999999')", refused("[1.2.3.99999999999]")},
            {SET_ALLOWED + "('/8')", refused("[/8]")},
            {SET_ALLOWED + "('a b')", refused("[a b]")},
            {SET_ALLOWED + "('''q''')", refused("['q']")},
        });
    }

    @Test
    public void theFailingEntriesAreToldTogetherAsThePolicyWouldKeepThem() {
        assertCells(new String[][] {
            {SET_ALLOWED + "('abc', 'def')", refused("[abc, def]")},
            {SET_ALLOWED + "('1.2.3.4', 'def', '5.6.7.8', 'ghi')", refused("[def, ghi]")},
            {SET_ALLOWED + "('ABC', 'ABC')", refused("[ABC, ABC]")},
            // An entry is trimmed and then loses one slash at its end.
            {SET_ALLOWED + "(' abc ')", refused("[abc]")},
            {SET_ALLOWED + "('abc/')", refused("[abc]")},
            {SET_ALLOWED + "('abc//')", refused("[abc/]")},
            {SET_ALLOWED + "('abc /')", refused("[abc ]")},
            {SET_ALLOWED + "('abc/ ')", refused("[abc]")},
            {SET_ALLOWED + "('1.2.3.4/33/')", refused("[1.2.3.4/33]")},
            {SET_ALLOWED + "('')", refused("[]")},
            {SET_ALLOWED + "('/')", refused("[]")},
            {SET_ALLOWED + "('1.2.3.4', '')", refused("[]")},
            // A comma inside an entry separates several, and one at its end separates nothing.
            {SET_ALLOWED + "('a,b')", refused("[a, b]")},
            {SET_ALLOWED + "('a , b')", refused("[a, b]")},
            {SET_ALLOWED + "('a,')", refused("[a]")},
            {SET_ALLOWED + "('1.2.3.4,abc/')", refused("[abc]")},
            {SET_ALLOWED + "('1.2.3.4,,5.6.7.8')", refused("[]")},
            // The allowed list is judged before the blocked one, whichever is written first.
            {SET_ALLOWED + "('1.2.3.4') BLOCKED_IP_LIST = ('zz')", refused("[zz]")},
            {SET_ALLOWED + "('yy') BLOCKED_IP_LIST = ('zz')", refused("[yy]")},
            {"ALTER NETWORK POLICY " + POLICY + " SET BLOCKED_IP_LIST = ('zz') ALLOWED_IP_LIST = ('yy')",
                refused("[yy]")},
            {SET_ALLOWED + "('1.2.3.4', 'x') COMMENT = 'c'", refused("[x]")},
        });
        // A refused list leaves the policy as it was.
        assertAnswer("ALLOWED_IP_LIST|1.2.3.4", "DESCRIBE NETWORK POLICY " + POLICY);
    }

    @Test
    public void anAddressIsReadLeniently() {
        for (final String list : new String[] {"('1.2.3.4/32')", "('0.0.0.0/0')", "('0.0.0.0')", "('1.2.3.4/0')",
            "('01.2.3.4')", "('1.2.3.04')", "('1.2.3.0004')", "('+1.2.3.4')", "('1.2.3.+4')", "('1.2.3.-0')",
            "('1.2.3.4/08')", "('1.2.3.4/032')", "('1.2.3.4/+8')", "('1.2.3.4/ 8')", "('1.2.3.4 /8')",
            "('1.2.3.4.')", "('1.2.3.4./8')", "('1.2.3.4. ')", "('1.2.3.4/8/')", "('1.2.3.4', '1.2.3.4')",
            "('1.2.3.4,5.6.7.8')", "('1.2.3.4,')", "(',')", "()"}) {
            assertTaken(SET_ALLOWED + list);
        }
    }

    @Test
    public void aPolicyKeepsItsAddressesTrimmedAndWithoutTheSlashAtTheirEnd() {
        assertTaken(SET_ALLOWED + "(' 1.2.3.4 ', '5.6.7.8/', '1.1.1.1,2.2.2.2', '+1.2.3.4', '01.2.3.4/08')");
        assertAnswer("ALLOWED_IP_LIST|1.2.3.4,5.6.7.8,1.1.1.1,2.2.2.2,+1.2.3.4,01.2.3.4/08",
            "DESCRIBE NETWORK POLICY " + POLICY);
        assertTaken(SET_ALLOWED
            + "('1.2.3.4 /8', '1.2.3.5/ 9', '1.2.3.6.', '1.2.3.4', '1.2.3.4', ',', '1.2.3.7,')");
        assertAnswer("ALLOWED_IP_LIST|1.2.3.4 /8,1.2.3.5/ 9,1.2.3.6.,1.2.3.4,1.2.3.4,1.2.3.7",
            "DESCRIBE NETWORK POLICY " + POLICY);
        assertAnswer("6", "SHOW NETWORK POLICIES LIKE '" + POLICY
            + "' ->> SELECT \"entries_in_allowed_ip_list\" FROM $1");
        assertTaken(SET_ALLOWED + "('1.2.3.4 / 8 ') BLOCKED_IP_LIST = ('5.6.7.8/')");
        assertAnswer("ALLOWED_IP_LIST|1.2.3.4 / 8 ;; BLOCKED_IP_LIST|5.6.7.8",
            "DESCRIBE NETWORK POLICY " + POLICY);
        assertTaken(SET_ALLOWED + "(',') BLOCKED_IP_LIST = ()");
        assertAnswer("", "DESCRIBE NETWORK POLICY " + POLICY);
    }

    @Test
    public void aNewPolicyIsJudgedOnceItsNameIs() {
        final String create = "CREATE NETWORK POLICY " + POLICY;
        assertCells(new String[][] {
            // The name is judged first: a policy that is there is refused for its name, or left alone.
            {create + " ALLOWED_IP_LIST = ('abc')",
                "REFUSED SQL compilation error: | Object '" + POLICY + "' already exists."},
            {"CREATE OR REPLACE NETWORK POLICY " + POLICY + " ALLOWED_IP_LIST = ('abc')", refused("[abc]")},
        });
        final String missing = answer("ALTER NETWORK POLICY NO_SUCH_ADDRESS_LIST_NP SET ALLOWED_IP_LIST = ('abc')");
        assertTrue(missing.startsWith("REFUSED SQL compilation error: | Network policy 'NO_SUCH_ADDRESS_LIST_NP' does"
            + " not exist or not authorized."), missing);
        assertTaken("CREATE NETWORK POLICY IF NOT EXISTS " + POLICY + " ALLOWED_IP_LIST = ('abc')");
        assertTaken("ALTER NETWORK POLICY IF EXISTS NO_SUCH_ADDRESS_LIST_NP SET ALLOWED_IP_LIST = ('abc')");
        assertAnswer("ALLOWED_IP_LIST|1.2.3.4", "DESCRIBE NETWORK POLICY " + POLICY);
        engine.execute("DROP NETWORK POLICY " + POLICY);
        assertCells(new String[][] {
            {create + " ALLOWED_IP_LIST = ('abc')", refused("[abc]")},
            {create + " BLOCKED_IP_LIST = ('abc')", refused("[abc]")},
            {create + " ALLOWED_IP_LIST = ('1.2.3.4') BLOCKED_IP_LIST = ('abc', '1.1.1.1/40')",
                refused("[abc, 1.1.1.1/40]")},
            {create + " ALLOWED_IP_LIST = ('p') BLOCKED_IP_LIST = ('q')", refused("[p]")},
            {create + " BLOCKED_IP_LIST = ('q') ALLOWED_IP_LIST = ('p')", refused("[p]")},
        });
        assertTaken(create + " ALLOWED_IP_LIST = ('1.2.3.4/24', '0.0.0.0/0')");
        assertAnswer("ALLOWED_IP_LIST|1.2.3.4/24,0.0.0.0/0", "DESCRIBE NETWORK POLICY " + POLICY);
    }

    @Test
    public void aSessionVariableIsJudgedAsTheTextItHolds() {
        engine.execute("SET fl_address = 'xyz/'");
        assertAnswer(refused("[xyz]"), SET_ALLOWED + "($fl_address)");
    }
}
