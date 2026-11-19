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

/**
 * A replication listing narrowed to one primary (live-verified): SHOW REPLICATION DATABASES, SHOW REPLICATION GROUPS
 * and SHOW FAILOVER GROUPS read {@code WITH PRIMARY <name>} after their LIKE, the name of one part or several, and
 * nothing after it; SHOW REPLICATION ACCOUNTS does not, and a statement without the name stops where it is due.
 */
public class ShowReplicationPrimaryTest extends MeasuredCellsTestSupport {

    @Test
    public void withPrimaryAndItsName() {
        assertCells(new String[][] {
            {"SHOW REPLICATION DATABASES",
                ""},
            {"SHOW REPLICATION DATABASES LIKE 'PRB%'",
                ""},
            {"SHOW REPLICATION DATABASES WITH PRIMARY \"ABCORG\".\"ABC12345\".\"PRB_TF\"",
                ""},
            {"SHOW REPLICATION DATABASES WITH PRIMARY nosuchorg.nosuchacct.nosuchdb",
                ""},
            {"SHOW REPLICATION DATABASES WITH PRIMARY PRB_TF",
                ""},
            {"SHOW REPLICATION DATABASES LIKE 'X' WITH PRIMARY a.b.c",
                ""},
            {"SHOW REPLICATION DATABASES WITH PRIMARY a.b",
                ""},
            {"SHOW REPLICATION DATABASES WITH PRIMARY a.b.c LIKE 'X'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'LIKE'."},
            {"SHOW REPLICATION DATABASES ->> SELECT COUNT(*) FROM $1",
                "0"},
            {"SHOW REPLICATION DATABASES WITH PRIMARY a.b.c",
                ""},
            {"SHOW REPLICATION DATABASES WITH PRIMARY a.b.c.d",
                ""},
            {"SHOW REPLICATION DATABASES WITH PRIMARY",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '<EOF>'."},
            {"SHOW REPLICATION DATABASES WITH PRIMARY 'a'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ''a''."},
            {"SHOW REPLICATION DATABASES WITH PRIMARY a.b.c IN ACCOUNT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'IN'."},
            {"SHOW REPLICATION DATABASES WITH PRIMARY a.b.c LIMIT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'LIMIT'."},
            {"SHOW REPLICATION DATABASES WITH PRIMARY a.b.c STARTS WITH 'A'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'STARTS'."},
            {"SHOW REPLICATION GROUPS WITH PRIMARY a.b.c",
                ""},
            {"SHOW REPLICATION ACCOUNTS WITH PRIMARY a.b.c",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'PRIMARY'."},
            {"SHOW FAILOVER GROUPS WITH PRIMARY a.b.c",
                ""},
            {"SHOW REPLICATION DATABASES LIKE 'X' WITH PRIMARY a.b.c",
                ""},
            {"SHOW REPLICATION DATABASES WITH PRIMARY IDENTIFIER('a.b.c')",
                ""},
            {"SHOW REPLICATION DATABASES WITH PRIMARY \"a b\".\"c\".\"d\"",
                ""},
            {"SHOW REPLICATION DATABASES WITH PRIMARY a.b.c ->> SELECT COUNT(*) FROM $1",
                "0"},
            {"SHOW REPLICATION DATABASES WITH PRIMARY a..c",
                ""},
            {"SHOW REPLICATION DATABASES WITH PRIMARY a.b.c;",
                ""},
            {"SHOW REPLICATION DATABASES WITH PRIMARY a.b.c x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'x'."},
            {"SHOW REPLICATION DATABASES WITH PRIMARY $v",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '$v'."},
            {"SHOW REPLICATION DATABASES WITH PRIMARY a.b.c WITH PRIMARY a.b.c",
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'PRIMARY'."},
            {"SHOW REPLICATION DATABASES IN ACCOUNT x WITH PRIMARY a.b.c",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'PRIMARY'."},
            {"SHOW REPLICATION DATABASES WITH PRIMARY 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '1'."},
        });
    }
}
