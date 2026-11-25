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

package dev.frostlake.features;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * SYSTEM$STREAM_HAS_DATA counts the versions a statement wrote past a stream's offset, not the rows the stream lists:
 * a DELETE that leaves rows, an UPDATE and a MERGE count for an append-only stream too, a TRUNCATE and a DELETE of
 * every row for a standard stream alone, a MERGE whatever its branches did where the table holds rows, and an
 * UPDATE or a DELETE that changed no row for none, nor a MERGE that left an empty table as it was (all
 * live-verified).
 */
public class StreamHasDataDmlVersionsTest extends MeasuredCellsTestSupport {

    private static final String BOTH = "SELECT SYSTEM$STREAM_HAS_DATA('ao1'), SYSTEM$STREAM_HAS_DATA('st1')";

    @Test
    public void eachStatementCountsByWhatItWrote() {
        engine.execute("CREATE TABLE t1 (id INT, v VARCHAR)");
        engine.execute("INSERT INTO t1 VALUES (1, 'a'), (2, 'b'), (3, 'c')");
        engine.execute("CREATE STREAM ao1 ON TABLE t1 APPEND_ONLY = TRUE");
        engine.execute("CREATE STREAM st1 ON TABLE t1");
        engine.execute("CREATE TABLE sink (id INT)");
        assertAnswer("false|false", BOTH);
        engine.execute("DELETE FROM t1 WHERE id = 1");
        assertAnswer("true|true", BOTH);
        consume();
        assertAnswer("false|false", BOTH);
        engine.execute("UPDATE t1 SET v = 'z' WHERE id = 2");
        assertAnswer("true|true", BOTH);
        consume();
        engine.execute("UPDATE t1 SET v = 'q' WHERE id = 99");
        assertAnswer("false|false", BOTH);
        engine.execute("MERGE INTO t1 USING (SELECT 2 AS id) s ON t1.id = s.id WHEN MATCHED THEN UPDATE SET v = 'm'");
        assertAnswer("true|true", BOTH);
        consume();
        engine.execute("MERGE INTO t1 USING (SELECT 99 AS id) s ON t1.id = s.id WHEN MATCHED THEN UPDATE SET v = 'm'");
        assertAnswer("true|true", BOTH);
        consume();
        engine.execute("TRUNCATE TABLE t1");
        assertAnswer("false|true", BOTH);
        assertAnswer("2", "SELECT COUNT(*) FROM st1");
        consume();
        engine.execute("TRUNCATE TABLE t1");
        assertAnswer("false|false", BOTH);
        engine.execute("DELETE FROM t1");
        assertAnswer("false|false", BOTH);
        engine.execute("INSERT INTO t1 VALUES (5, 'e'), (6, 'f')");
        consume();
        engine.execute("DELETE FROM t1 WHERE id = 5");
        assertAnswer("true|true", BOTH);
        consume();
        engine.execute("INSERT OVERWRITE INTO t1 VALUES (7, 'g')");
        assertAnswer("true|true", BOTH);
        assertAnswer("2", "SELECT COUNT(*) FROM st1");
    }

    @Test
    public void aStatementThatChangedNoRowCountsByTheTable() {
        assertNoRowStatement("tu", "UPDATE tu SET v = 'q' WHERE id = 99", "UPDATE|0|0|0", "false|false");
        assertNoRowStatement("td", "DELETE FROM td WHERE id = 99", "UPDATE|0|0|0", "false|false");
        assertNoRowStatement("tm",
            "MERGE INTO tm USING (SELECT 99 AS id) s ON tm.id = s.id WHEN MATCHED THEN UPDATE SET v = 'm'",
            "UPDATE|2|2|1", "true|true");
        assertNoRowStatement("tn",
            "MERGE INTO tn USING (SELECT 1 AS id) s ON tn.id = s.id WHEN NOT MATCHED THEN INSERT VALUES (s.id, 'n')",
            "UPDATE|2|2|1", "true|true");
        assertNoRowStatement("tx", "UPDATE tx SET v = v WHERE id = 1", "UPDATE|2|2|1", "true|true");
        assertNoRowStatement("tt", "DELETE FROM tt WHERE id = 1", "UPDATE|1|2|1", "true|true");
        assertNoRowStatement("tw",
            "MERGE INTO tw USING (SELECT 1 AS id) s ON tw.id = s.id WHEN MATCHED THEN DELETE",
            "UPDATE|1|2|1", "true|true");
    }

    @Test
    public void aMergeThatLeftAnEmptyTableAsItWasCountsForNone() {
        engine.execute("CREATE TABLE te (id INT, v VARCHAR)");
        engine.execute("CREATE STREAM te_ao ON TABLE te APPEND_ONLY = TRUE");
        engine.execute("CREATE STREAM te_st ON TABLE te");
        engine.execute("MERGE INTO te USING (SELECT 1 AS id) s ON te.id = s.id WHEN MATCHED THEN DELETE");
        assertAnswer("UPDATE|0|0|0", backlog("te_st"));
        assertAnswer("false|false", "SELECT SYSTEM$STREAM_HAS_DATA('te_ao'), SYSTEM$STREAM_HAS_DATA('te_st')");
    }

    private void assertNoRowStatement(final String table, final String statement, final String version,
                                      final String hasData) {
        engine.execute("CREATE TABLE " + table + " (id INT, v VARCHAR)");
        engine.execute("INSERT INTO " + table + " VALUES (1, 'a'), (2, 'b')");
        engine.execute("CREATE STREAM " + table + "_ao ON TABLE " + table + " APPEND_ONLY = TRUE");
        engine.execute("CREATE STREAM " + table + "_st ON TABLE " + table);
        engine.execute(statement);
        assertAnswer(version, backlog(table + "_st"));
        assertAnswer(hasData, "SELECT SYSTEM$STREAM_HAS_DATA('" + table + "_ao'), SYSTEM$STREAM_HAS_DATA('" + table
            + "_st')");
    }

    private static String backlog(final String stream) {
        return "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM TABLE(SYSTEM$STREAM_BACKLOG('"
            + stream + "'))";
    }

    private void consume() {
        engine.execute("INSERT INTO sink SELECT id FROM ao1");
        engine.execute("INSERT INTO sink SELECT id FROM st1");
    }
}
