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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * The scopes SHOW ACCOUNTS, SHOW INTEGRATIONS and the security object listings read after IN: the account
 * listing refuses any scope whole (a kind word with or without a name, a plain word naming the kind of a second
 * name, a failover or replication group), and a keyword or a second name it does not read where it stands; the
 * integration listing reads IN ACCOUNT and the account's name and nothing else; a network policy, network rule,
 * secret or password policy listing refuses a failover or a replication group as a scope (live-verified).
 */
public class ShowScopeWordsTest extends MeasuredCellsTestSupport {

    @Test
    public void accountsScope() {
        assertCells(new String[][] {
            {"SHOW ACCOUNTS IN ORGANIZATION x",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN ORGANIZATION",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN ACCOUNT",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN ACCOUNT x",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN DATABASE x",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN x",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN",
                "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '<EOF>'."},
            {"SHOW ACCOUNTS HISTORY IN ORGANIZATION x",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS LIKE 'x' IN ORGANIZATION x",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN ORGANIZATION x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'y'."},
            {"SHOW ACCOUNTS IN ORGANIZATION x.y",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN ORGANIZATION 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''."},
            {"SHOW ACCOUNTS IN FAILOVER GROUP x",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN REPLICATION GROUP x",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN SCHEMA x",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN ORGANIZATION LIMIT 1",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN ORGANIZATION x LIMIT 1",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN APPLICATION x",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN USER x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x'."},
            {"SHOW ACCOUNTS x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'."},
            {"SHOW TERSE ACCOUNTS IN ORGANIZATION x",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ORGANIZATION ACCOUNTS'."},
            {"SHOW ACCOUNTS IN ORGANIZATION IDENTIFIER('x')",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN ORGANIZATION \"x\"",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN ORGANIZATION ORGANIZATION",
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '<EOF>'."},
            {"SHOW ACCOUNTS IN ORGANIZATION x STARTS WITH 'a'",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ORGANIZATION ACCOUNTS IN ORGANIZATION x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'IN'."},
            {"SHOW ACCOUNTS IN TABLE x",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN ORGANIZATION ORGANIZATION x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'x'."},
            {"SHOW ACCOUNTS IN ORGANIZATION ACCOUNT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '<EOF>'."},
            {"SHOW ACCOUNTS IN ORGANIZATION ACCOUNT x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'x'."},
            {"SHOW ACCOUNTS IN ACCOUNT ACCOUNT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '<EOF>'."},
            {"SHOW ACCOUNTS IN DATABASE DATABASE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '<EOF>'."},
            {"SHOW ACCOUNTS IN APPLICATION PACKAGE x",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN COMPUTE POOL x",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN ORGANIZATION x.y.z",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN FAILOVER x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'."},
            {"SHOW ACCOUNTS IN FAILOVER GROUP",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN REPLICATION x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'x'."},
            {"SHOW ACCOUNTS IN FAILOVER GROUP x.y",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN FAILOVER GROUP x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'y'."},
            {"SHOW ACCOUNTS IN ORGANIZATION x.y.z.w",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN ACCOUNT x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'y'."},
            {"SHOW ACCOUNTS IN x y",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN ORGANIZATION ORGANIZATION ACCOUNTS",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'ACCOUNTS'."},
            {"SHOW ACCOUNTS IN ORGANIZATION ORGANIZATION ORGANIZATION",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected "
                + "'ORGANIZATION'."},
            {"SHOW ACCOUNTS IN ORGANIZATION x HISTORY",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'HISTORY'."},
            {"SHOW ACCOUNTS IN ORGANIZATION x LIKE 'a'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'LIKE'."},
            {"SHOW ACCOUNTS IN DATABASE x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'y'."},
            {"SHOW ACCOUNTS IN ROLE x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x'."},
            {"SHOW ACCOUNTS IN SHARE x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'."},
            {"SHOW ACCOUNTS IN ORGANIZATION ORGANIZATION.x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '<EOF>'."},
            {"SHOW ACCOUNTS IN FAILOVER GROUP IDENTIFIER('x')",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN ORGANIZATION ;",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN USER",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN WAREHOUSE x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'."},
            {"SHOW ACCOUNTS IN ORGANIZATION ACCOUNTS",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN ORGANIZATION x WITH PRIVILEGES USAGE",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN x.y z",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN \"x\" y",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN IDENTIFIER('x') y",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN FAILOVER",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN REPLICATION",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN GROUP x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'GROUP'."},
            {"SHOW ACCOUNTS IN APPLICATION x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'y'."},
            {"SHOW ACCOUNTS IN COMPUTE x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'."},
            {"SHOW ACCOUNTS IN PACKAGE x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'."},
            {"SHOW ACCOUNTS IN POOL x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x'."},
            {"SHOW ACCOUNTS IN TASK x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x'."},
            {"SHOW ACCOUNTS IN STAGE x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'."},
            {"SHOW ACCOUNTS IN VIEW x",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN INTEGRATION x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'x'."},
            {"SHOW ACCOUNTS IN STREAM x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'."},
            {"SHOW ACCOUNTS IN x y z",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'z'."},
            {"SHOW ACCOUNTS IN FUNCTION x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'."},
            {"SHOW ACCOUNTS IN PROCEDURE x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'."},
            {"SHOW ACCOUNTS IN x y.z",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN x IDENTIFIER('y')",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN x 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ''y''."},
            {"SHOW TERSE ACCOUNTS IN x",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ORGANIZATION ACCOUNTS'."},
            {"SHOW TERSE ACCOUNTS IN ACCOUNT",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ORGANIZATION ACCOUNTS'."},
            {"SHOW ACCOUNTS IN ACCOUNTS x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'."},
            {"SHOW ACCOUNTS IN ALERT x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'."},
            {"SHOW ACCOUNTS IN SEQUENCE x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'."},
            {"SHOW ACCOUNTS IN NETWORK x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'."},
            {"SHOW ACCOUNTS IN WAREHOUSE",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN ROLE",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN SHARE",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN COMPUTE POOL",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '<EOF>'."},
            {"SHOW ACCOUNTS IN APPLICATION PACKAGE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '<EOF>'."},
            {"SHOW ACCOUNTS IN ORGANIZATION x.y z",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'z'."},
            {"SHOW ACCOUNTS IN SECURITY x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'."},
            {"SHOW ACCOUNTS IN EXTERNAL x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'."},
            {"SHOW ACCOUNTS IN LIMIT x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'."},
            {"SHOW ACCOUNTS IN LIMIT",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW ACCOUNTS IN TAG x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x'."},
            {"SHOW ACCOUNTS IN TYPE x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x'."},
            {"SHOW ACCOUNTS IN STARTS x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'."},
        });
    }

    @Test
    public void integrationsScope() {
        assertCells(new String[][] {
            {"SHOW INTEGRATIONS IN FAILOVER GROUP x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'FAILOVER'."},
            {"SHOW INTEGRATIONS IN REPLICATION GROUP x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected "
                + "'REPLICATION'."},
            {"SHOW INTEGRATIONS IN ACCOUNT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '<EOF>'."},
            {"SHOW INTEGRATIONS IN DATABASE x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'DATABASE'."},
            {"SHOW INTEGRATIONS IN x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x'."},
            {"SHOW INTEGRATIONS IN",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '<EOF>'."},
            {"SHOW INTEGRATIONS LIKE 'x' IN FAILOVER GROUP x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'FAILOVER'."},
            {"SHOW API INTEGRATIONS IN FAILOVER GROUP x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'FAILOVER'."},
            {"SHOW STORAGE INTEGRATIONS IN ACCOUNT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '<EOF>'."},
            {"SHOW INTEGRATIONS IN ORGANIZATION x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected "
                + "'ORGANIZATION'."},
            {"SHOW INTEGRATIONS FAILOVER",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'FAILOVER'."},
            {"SHOW INTEGRATIONS LIMIT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'LIMIT'."},
            {"SHOW INTEGRATIONS IN SCHEMA x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'SCHEMA'."},
            {"SHOW SECURITY INTEGRATIONS IN REPLICATION GROUP x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected "
                + "'REPLICATION'."},
            {"SHOW NOTIFICATION INTEGRATIONS IN x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'x'."},
            {"SHOW DATABASES IN FAILOVER GROUP x",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW DATABASES IN REPLICATION GROUP x",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW ROLES IN FAILOVER GROUP x",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "ROLE in FAILOVER GROUP'."},
            {"SHOW USERS IN REPLICATION GROUP x",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "USER in REPLICATION GROUP'."},
            {"SHOW WAREHOUSES IN FAILOVER GROUP x",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "WAREHOUSE in FAILOVER GROUP'."},
            {"SHOW TABLES IN FAILOVER GROUP x",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "TABLE in FAILOVER GROUP'."},
            {"SHOW SCHEMAS IN REPLICATION GROUP x",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "SCHEMA in REPLICATION GROUP'."},
            {"SHOW ACCOUNTS IN FAILOVER GROUP x",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW RESOURCE MONITORS IN FAILOVER GROUP x",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "RESOURCE MONITOR in FAILOVER GROUP'."},
            {"SHOW FAILOVER GROUPS IN FAILOVER GROUP x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'GROUP'."},
            {"SHOW REPLICATION GROUPS IN REPLICATION GROUP x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'GROUP'."},
            {"SHOW DATABASES IN ORGANIZATION x",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database "
                + "for X"},
            {"SHOW CONNECTIONS IN FAILOVER GROUP x",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "CONNECTION in FAILOVER GROUP'."},
            {"SHOW INTEGRATIONS IN ACCOUNT x",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW INTEGRATIONS IN ACCOUNT x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'y'."},
            {"SHOW INTEGRATIONS IN ACCOUNT 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ''x''."},
            {"SHOW INTEGRATIONS IN ACCOUNT x.y",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW INTEGRATIONS IN ACCOUNT LIMIT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '1'."},
            {"SHOW INTEGRATIONS LIKE 'x' IN ACCOUNT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '<EOF>'."},
            {"SHOW INTEGRATIONS IN 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ''x''."},
            {"SHOW INTEGRATIONS IN ACCOUNT ;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ';'."},
            {"SHOW INTEGRATIONS IN ACCOUNT IDENTIFIER('x')",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW INTEGRATIONS IN ACCOUNT ACCOUNT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '<EOF>'."},
            {"SHOW INTEGRATIONS IN IN",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'IN'."},
            {"SHOW INTEGRATIONS IN ;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ';'."},
            {"SHOW INTEGRATIONS IN (",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '('."},
            {"SHOW INTEGRATIONS IN ACCOUNT x LIKE 'a'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'LIKE'."},
            {"SHOW INTEGRATIONS IN ACCOUNT TO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'TO'."},
            {"SHOW INTEGRATIONS IN ACCOUNT FOR x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'FOR'."},
            {"SHOW INTEGRATIONS IN ACCOUNT (",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '('."},
            {"SHOW NETWORK POLICIES IN ACCOUNT",
                ""},
            {"SHOW NETWORK POLICIES IN DATABASE x",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "NETWORK POLICY in DATABASE'."},
            {"SHOW INTEGRATIONS HISTORY",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'HISTORY'."},
            {"SHOW INTEGRATIONS IN ACCOUNT x;",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW INTEGRATIONS IN ACCOUNT PZ33098",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW INTEGRATIONS IN ACCOUNT x.y.z",
                "REFUSED SQL compilation error: account identifier 'X.Y.Z' has too many qualifiers"},
            {"SHOW INTEGRATIONS IN ACCOUNT ZU08528 LIMIT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'LIMIT'."},
            {"SHOW API INTEGRATIONS IN ACCOUNT x",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW ACCOUNTS IN x GROUP y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'GROUP'."},
            {"SHOW ACCOUNTS IN FAILOVER GROUP x.y.z.w",
                "REFUSED SQL compilation error: | Unsupported statement type. correct syntax: 'SHOW "
                + "ACCOUNTS'."},
            {"SHOW INTEGRATIONS IN ACCOUNT DATABASE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '<EOF>'."},
            {"SHOW INTEGRATIONS IN ACCOUNT ORGANIZATION",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '<EOF>'."},
            {"SHOW INTEGRATIONS IN ACCOUNT ROLE",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
        });
    }

    @Test
    public void ownAccount() {
        engine.execute("SET own_loc = CURRENT_ACCOUNT()");
        engine.execute("SET own_org = CURRENT_ORGANIZATION_NAME() || '.' || CURRENT_ACCOUNT_NAME()");
        assertCells(new String[][] {
            {"SHOW INTEGRATIONS LIKE 'NO_SUCH%' IN ACCOUNT IDENTIFIER($own_loc)",
                ""},
            {"SHOW INTEGRATIONS LIKE 'NO_SUCH%' IN ACCOUNT IDENTIFIER($own_org)",
                ""},
            {"SHOW STORAGE INTEGRATIONS LIKE 'NO_SUCH%' IN ACCOUNT IDENTIFIER($own_loc)",
                ""},
            {"SHOW INTEGRATIONS IN ACCOUNT x.y.z",
                "REFUSED SQL compilation error: account identifier 'X.Y.Z' has too many qualifiers"},
            {"SHOW INTEGRATIONS IN ACCOUNT x.y",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW INTEGRATIONS IN ACCOUNT ZZ99999 LIMIT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'LIMIT'."},
        });
    }

    @Test
    public void groupScopes() {
        assertCells(new String[][] {
            {"SHOW NETWORK POLICIES IN FAILOVER GROUP x",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "NETWORK POLICY in FAILOVER GROUP'."},
            {"SHOW NETWORK POLICIES IN REPLICATION GROUP x",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "NETWORK POLICY in REPLICATION GROUP'."},
            {"SHOW NETWORK POLICIES IN FAILOVER GROUP",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "NETWORK POLICY in FAILOVER GROUP'."},
            {"SHOW NETWORK POLICIES IN FAILOVER x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'x'."},
            {"SHOW NETWORK POLICIES IN x GROUP y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'GROUP'."},
            {"SHOW NETWORK RULES IN FAILOVER GROUP x",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "NETWORK RULE in FAILOVER GROUP'."},
            {"SHOW SECRETS IN FAILOVER GROUP x",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "SECRET in FAILOVER GROUP'."},
            {"SHOW PASSWORD POLICIES IN FAILOVER GROUP x",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "PASSWORD POLICY in FAILOVER GROUP'."},
            {"SHOW NETWORK POLICIES IN FAILOVER GROUP x LIMIT 1",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "NETWORK POLICY in FAILOVER GROUP'."},
            {"SHOW NETWORK POLICIES IN FAILOVER GROUP x.y.z",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "NETWORK POLICY in FAILOVER GROUP'."},
            {"SHOW NETWORK POLICIES LIKE 'a' IN FAILOVER GROUP x",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "NETWORK POLICY in FAILOVER GROUP'."},
            {"SHOW NETWORK RULES IN REPLICATION GROUP x",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "NETWORK RULE in REPLICATION GROUP'."},
            {"SHOW SECRETS IN REPLICATION GROUP",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "SECRET in REPLICATION GROUP'."},
        });
    }
}
