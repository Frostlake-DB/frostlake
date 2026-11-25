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
 * How CREATE and ALTER of connections and of replication groups are read, by statements that never run - each is
 * refused, most at a stray parenthesis after a statement the account reads whole (live-verified): the connection's
 * name, AS REPLICA OF and COMMENT; ALTER
 * CONNECTION's SET and UNSET COMMENT, PRIMARY, and ENABLE and DISABLE FAILOVER, which take no IGNORE EDITION CHECK;
 * a replication group's properties and its replica; COMMENT ON CONNECTION; and what is refused where.
 */
public class ConnectionAndReplicationGroupSyntaxTest extends MeasuredCellsTestSupport {

    @Test
    public void statementsAreReadAsTheAccountReadsThem() {
        assertCells(new String[][] {
            {"CREATE CONNECTION \"PRB_EAV_C\" COMMENT = 'one' )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected ')'."},
            {"CREATE CONNECTION \"PRB_EAV_C\" AS REPLICA OF \"o\".\"a\".\"C\" COMMENT = 'one' )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 72 unexpected ')'."},
            {"CREATE CONNECTION IF NOT EXISTS PRB_EAV_C )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ')'."},
            {"ALTER CONNECTION PRB_EAV_C SET COMMENT = 'x' )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected ')'."},
            {"ALTER CONNECTION PRB_EAV_C UNSET COMMENT )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ')'."},
            {"ALTER CONNECTION PRB_EAV_C PRIMARY )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ')'."},
            {"ALTER CONNECTION PRB_EAV_C ENABLE FAILOVER TO ACCOUNTS o.a )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected ')'."},
            {"CREATE REPLICATION GROUP \"PRB_EAV_RG\" OBJECT_TYPES = DATABASES ALLOWED_DATABASES = \"D\" "
                + "ALLOWED_ACCOUNTS = \"o\".\"a\" )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 114 unexpected ')'."},
            {"CREATE REPLICATION GROUP \"PRB_EAV_RG\" AS REPLICA OF \"o\".\"a\".\"RG\" )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected ')'."},
            {"ALTER REPLICATION GROUP PRB_EAV_RG SET COMMENT = 'x' )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected ')'."},
            {"CREATE FAILOVER GROUP \"PRB_EAV_FG\" OBJECT_TYPES = DATABASES ALLOWED_ACCOUNTS = \"o\".\"a\""
                + " )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 87 unexpected ')'."},
            {"COMMENT ON CONNECTION PRB_EAV_C IS 'x' )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ')'."},
            {"CREATE FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '<EOF>'."},
            {"CREATE FOO GROUP x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'GROUP'."},
            {"CREATE CONNECTION",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '<EOF>'."},
            {"CREATE CONNECTION x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '<EOF>'."},
            {"CREATE CONNECTION x AS REPLICA y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'y'."},
            {"CREATE CONNECTION x COMMENT 'a'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ''a''."},
            {"CREATE REPLICATION GROUP",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '<EOF>'."},
            {"CREATE connection x as replica of a.b.c comment = 'z' )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected ')'."},
            {"ALTER REPLICATION GROUP x PRIMARY )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ')'."},
            {"ALTER REPLICATION GROUP x REFRESH )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ')'."},
            {"ALTER REPLICATION GROUP x SUSPEND )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ')'."},
            {"ALTER FAILOVER GROUP x PRIMARY )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ')'."},
            {"ALTER CONNECTION x DISABLE FAILOVER )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected ')'."},
            {"ALTER CONNECTION x DISABLE FAILOVER TO ACCOUNTS o.a, o.b )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected ')'."},
            {"ALTER CONNECTION x ENABLE FAILOVER TO ACCOUNTS o.a IGNORE EDITION CHECK )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'IGNORE'."},
            {"ALTER FOO x PRIMARY",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'PRIMARY'."},
            {"CREATE CONNECTION x AS REPLICA OF a.b )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ')'."},
            {"ALTER CONNECTION IF EXISTS x PRIMARY )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ')'."},
            {"CREATE REPLICATION GROUPS x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'GROUPS'."},
        });
    }
}
