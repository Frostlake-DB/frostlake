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

package dev.frostlake.stream;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A stream on a view over two tables, one of which was dropped (and maybe replaced under its name): it still captures
 * the changes of the table that is there, which it reads once UNDROP brings the other back, and SYSTEM$STREAM_HAS_DATA
 * answers TRUE where a table that replaced a dropped one holds rows or where the table that is there took a change past
 * the stream's offset, not for the changes it held of the dropped table (all live-verified).
 */
public class ViewStreamGoneTableTest extends MeasuredCellsTestSupport {

    private static final String UNTRACKED = "REFUSED SQL compilation error: Change tracking is not enabled or has been"
        + " missing for the time range requested on table ";

    private void twoTables(final String schema) {
        engine.execute("CREATE OR REPLACE SCHEMA " + schema);
        engine.execute("CREATE TABLE " + schema + ".a (x INT)");
        engine.execute("CREATE TABLE " + schema + ".b (x INT)");
        engine.execute("CREATE VIEW " + schema + ".v AS SELECT x FROM " + schema + ".a UNION ALL SELECT x FROM "
            + schema + ".b");
        engine.execute("CREATE STREAM " + schema + ".s ON VIEW " + schema + ".v");
    }

    @Test
    public void hasDataOverReplacedTables() {
        twoTables("u1");
        engine.execute("INSERT INTO u1.a VALUES (1)");
        engine.execute("DROP TABLE u1.a");
        engine.execute("CREATE TABLE u1.a (x INT)");
        engine.execute("DROP TABLE u1.b");
        engine.execute("CREATE TABLE u1.b (x INT)");
        engine.execute("INSERT INTO u1.b VALUES (5)");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('u1.s')");
        assertAnswer(UNTRACKED + "'TEST_DB.U1.A'.", "SELECT x FROM u1.s ORDER BY 1");
        twoTables("u3");
        engine.execute("INSERT INTO u3.b VALUES (1)");
        engine.execute("DROP TABLE u3.a");
        engine.execute("CREATE TABLE u3.a (x INT)");
        engine.execute("DROP TABLE u3.b");
        engine.execute("CREATE TABLE u3.b (x INT)");
        engine.execute("INSERT INTO u3.a VALUES (5)");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('u3.s')");
        assertAnswer(UNTRACKED + "'TEST_DB.U3.A'.", "SELECT x FROM u3.s ORDER BY 1");
    }

    @Test
    public void hasDataCountsTheChangesOfTheTableThatIsThere() {
        twoTables("u2");
        engine.execute("INSERT INTO u2.a VALUES (1)");
        engine.execute("DROP TABLE u2.b");
        engine.execute("CREATE TABLE u2.b (x INT)");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('u2.s')");
        engine.execute("INSERT INTO u2.b VALUES (5)");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('u2.s')");
        assertAnswer(UNTRACKED + "'TEST_DB.U2.B'.", "SELECT x FROM u2.s ORDER BY 1");
        engine.execute("CREATE OR REPLACE SCHEMA u5");
        engine.execute("CREATE TABLE u5.a (x INT)");
        engine.execute("INSERT INTO u5.a VALUES (7)");
        engine.execute("CREATE TABLE u5.b (x INT)");
        engine.execute("CREATE VIEW u5.v AS SELECT x FROM u5.a UNION ALL SELECT x FROM u5.b");
        engine.execute("CREATE STREAM u5.s ON VIEW u5.v");
        engine.execute("INSERT INTO u5.b VALUES (1)");
        engine.execute("DROP TABLE u5.b");
        engine.execute("CREATE TABLE u5.b (x INT)");
        assertAnswer("false", "SELECT SYSTEM$STREAM_HAS_DATA('u5.s')");
        engine.execute("CREATE OR REPLACE SCHEMA u7");
        engine.execute("CREATE TABLE u7.a (x INT)");
        engine.execute("INSERT INTO u7.a VALUES (7)");
        engine.execute("CREATE TABLE u7.b (x INT)");
        engine.execute("CREATE VIEW u7.v AS SELECT x FROM u7.a UNION ALL SELECT x FROM u7.b");
        engine.execute("CREATE STREAM u7.s ON VIEW u7.v");
        engine.execute("INSERT INTO u7.b VALUES (1)");
        engine.execute("DELETE FROM u7.a");
        engine.execute("DROP TABLE u7.b");
        engine.execute("CREATE TABLE u7.b (x INT)");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('u7.s')");
        twoTables("u8");
        engine.execute("INSERT INTO u8.b VALUES (1)");
        engine.execute("DROP TABLE u8.b");
        engine.execute("CREATE TABLE u8.b (x INT)");
        assertAnswer("false", "SELECT SYSTEM$STREAM_HAS_DATA('u8.s')");
        engine.execute("CREATE OR REPLACE SCHEMA u9");
        engine.execute("CREATE TABLE u9.a (x INT)");
        engine.execute("CREATE TABLE u9.b (x INT)");
        engine.execute("CREATE VIEW u9.v AS SELECT x FROM u9.a WHERE x > 100 UNION ALL SELECT x FROM u9.b");
        engine.execute("CREATE STREAM u9.s ON VIEW u9.v");
        engine.execute("INSERT INTO u9.b VALUES (1)");
        engine.execute("DROP TABLE u9.b");
        engine.execute("CREATE TABLE u9.b (x INT)");
        engine.execute("INSERT INTO u9.a VALUES (5)");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('u9.s')");
    }

    @Test
    public void theTableThatIsThereKeepsFeedingTheStream() {
        twoTables("u10");
        engine.execute("INSERT INTO u10.b VALUES (1)");
        engine.execute("DROP TABLE u10.b");
        engine.execute("INSERT INTO u10.a VALUES (5)");
        engine.execute("UNDROP TABLE u10.b");
        assertAnswer("1 ;; 5", "SELECT x FROM u10.s ORDER BY 1");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('u10.s')");
        twoTables("u11");
        engine.execute("INSERT INTO u11.b VALUES (1)");
        engine.execute("DROP TABLE u11.b");
        engine.execute("CREATE TABLE u11.b (x INT)");
        assertAnswer("false", "SELECT SYSTEM$STREAM_HAS_DATA('u11.s')");
    }

    @Test
    public void aStreamHoldingNoChangeIsReboundWhenTheOtherTableChanged() {
        twoTables("u12");
        engine.execute("DROP TABLE u12.b");
        engine.execute("INSERT INTO u12.a VALUES (5)");
        engine.execute("CREATE TABLE u12.b (x INT)");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('u12.s')");
        engine.execute("SHOW STREAMS IN SCHEMA u12");
        assertAnswer("S|TEST_DB.U12.A, TEST_DB.U12.B|false",
            "SELECT \"name\", \"base_tables\", \"stale\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(UNTRACKED + "'TEST_DB.U12.B'.", "SELECT x FROM u12.s ORDER BY 1");
        engine.execute("INSERT INTO u12.a VALUES (6)");
        assertAnswer(UNTRACKED + "'TEST_DB.U12.B'.", "SELECT x FROM u12.s ORDER BY 1");
        twoTables("u13");
        engine.execute("DROP TABLE u13.b");
        engine.execute("INSERT INTO u13.a VALUES (5)");
        engine.execute("CREATE TABLE u13.b (x INT)");
        assertAnswer(UNTRACKED + "'TEST_DB.U13.B'.", "SELECT x FROM u13.s ORDER BY 1");
        engine.execute("SHOW STREAMS IN SCHEMA u13");
        assertAnswer("S|TEST_DB.U13.A, TEST_DB.U13.B|false",
            "SELECT \"name\", \"base_tables\", \"stale\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        engine.execute("INSERT INTO u13.b VALUES (7)");
        assertAnswer(UNTRACKED + "'TEST_DB.U13.B'.", "SELECT x FROM u13.s ORDER BY 1");
    }
}
