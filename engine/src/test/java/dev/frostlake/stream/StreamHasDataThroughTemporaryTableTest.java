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
 * SYSTEM$STREAM_HAS_DATA of a join view stream whose table a temporary table that tracks changes hides in the session
 * answers by the temporary table's changes since the stream's offset (live-verified): the hidden table's changes no
 * longer count, the view's other tables' still do, and a write to the temporary table that changed no row counts for
 * nothing.
 */
public class StreamHasDataThroughTemporaryTableTest extends MeasuredCellsTestSupport {

    @Test
    public void hiddenTableChangesGiveWayToTheTemporaryTables() {
        engine.execute("CREATE TABLE t1 (id INT, v STRING)");
        engine.execute("CREATE TABLE u1 (id INT, w STRING)");
        engine.execute("INSERT INTO u1 VALUES (1, 'u1'), (2, 'u2')");
        engine.execute("CREATE VIEW v1 AS SELECT t1.id, t1.v, u1.w FROM t1 JOIN u1 ON t1.id = u1.id");
        engine.execute("CREATE STREAM st1 ON VIEW v1");
        engine.execute("INSERT INTO t1 VALUES (1, 'perm1')");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('st1')");
        engine.execute("CREATE TEMPORARY TABLE t1 (id INT, v STRING) CHANGE_TRACKING = TRUE");
        assertAnswer("false", "SELECT SYSTEM$STREAM_HAS_DATA('st1')");
        assertAnswer("", "SELECT id, v, w, METADATA$ACTION FROM st1 ORDER BY 1");
        engine.execute("CREATE TABLE t2 (id INT, v STRING)");
        engine.execute("CREATE TABLE u2 (id INT, w STRING)");
        engine.execute("INSERT INTO u2 VALUES (1, 'u1'), (2, 'u2')");
        engine.execute("CREATE VIEW v2 AS SELECT t2.id, t2.v, u2.w FROM t2 JOIN u2 ON t2.id = u2.id");
        engine.execute("CREATE STREAM st2 ON VIEW v2");
        assertAnswer("false", "SELECT SYSTEM$STREAM_HAS_DATA('st2')");
        engine.execute("CREATE TEMPORARY TABLE t2 (id INT, v STRING) CHANGE_TRACKING = TRUE");
        assertAnswer("false", "SELECT SYSTEM$STREAM_HAS_DATA('st2')");
        engine.execute("INSERT INTO t2 VALUES (1, 'temp1')");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('st2')");
        assertAnswer("1|temp1|u1|INSERT", "SELECT id, v, w, METADATA$ACTION FROM st2 ORDER BY 1");
        engine.execute("CREATE TABLE t3 (id INT, v STRING)");
        engine.execute("CREATE TABLE u3 (id INT, w STRING)");
        engine.execute("INSERT INTO u3 VALUES (1, 'u1'), (2, 'u2')");
        engine.execute("CREATE VIEW v3 AS SELECT t3.id, t3.v, u3.w FROM t3 JOIN u3 ON t3.id = u3.id");
        engine.execute("CREATE STREAM st3 ON VIEW v3");
        engine.execute("CREATE TEMPORARY TABLE t3 (id INT, v STRING) CHANGE_TRACKING = TRUE");
        engine.execute("INSERT INTO t3 VALUES (9, 'temp9')");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('st3')");
        assertAnswer("", "SELECT id, v, w, METADATA$ACTION FROM st3 ORDER BY 1");
        engine.execute("CREATE TABLE t4 (id INT, v STRING)");
        engine.execute("CREATE TABLE u4 (id INT, w STRING)");
        engine.execute("INSERT INTO u4 VALUES (1, 'u1'), (2, 'u2')");
        engine.execute("CREATE VIEW v4 AS SELECT t4.id, t4.v, u4.w FROM t4 JOIN u4 ON t4.id = u4.id");
        engine.execute("CREATE STREAM st4 ON VIEW v4");
        engine.execute("INSERT INTO t4 VALUES (1, 'perm1')");
        engine.execute("CREATE TEMPORARY TABLE t4 (id INT, v STRING) CHANGE_TRACKING = TRUE");
        engine.execute("INSERT INTO t4 VALUES (2, 'temp2')");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('st4')");
        assertAnswer("2|temp2|u2|INSERT", "SELECT id, v, w, METADATA$ACTION FROM st4 ORDER BY 1");
        engine.execute("CREATE TABLE t5 (id INT, v STRING)");
        engine.execute("CREATE TABLE u5 (id INT, w STRING)");
        engine.execute("CREATE VIEW v5 AS SELECT t5.id, t5.v, u5.w FROM t5 JOIN u5 ON t5.id = u5.id");
        engine.execute("CREATE STREAM st5 ON VIEW v5");
        engine.execute("CREATE TEMPORARY TABLE t5 (id INT, v STRING) CHANGE_TRACKING = TRUE");
        engine.execute("INSERT INTO u5 VALUES (1, 'u1')");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('st5')");
        assertAnswer("", "SELECT id, v, w, METADATA$ACTION FROM st5 ORDER BY 1");
        engine.execute("INSERT INTO t5 VALUES (1, 'temp1')");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('st5')");
        assertAnswer("1|temp1|u1|INSERT", "SELECT id, v, w, METADATA$ACTION FROM st5 ORDER BY 1");
    }

    @Test
    public void whatTheTemporaryTableTookSinceTheOffsetCounts() {
        engine.execute("CREATE TABLE ta (id INT, v STRING)");
        engine.execute("CREATE TABLE ua (id INT, w STRING)");
        engine.execute("INSERT INTO ua VALUES (1, 'u1')");
        engine.execute("CREATE VIEW va AS SELECT ta.id, ta.v, ua.w FROM ta JOIN ua ON ta.id = ua.id");
        engine.execute("CREATE STREAM sa ON VIEW va");
        engine.execute("CREATE TEMPORARY TABLE ta (id INT, v STRING) CHANGE_TRACKING = TRUE");
        engine.execute("INSERT INTO ta SELECT 1, 'x' WHERE FALSE");
        assertAnswer("false", "SELECT SYSTEM$STREAM_HAS_DATA('sa')");
        engine.execute("CREATE TABLE tb (id INT, v STRING)");
        engine.execute("CREATE TABLE ub (id INT, w STRING)");
        engine.execute("INSERT INTO ub VALUES (1, 'u1')");
        engine.execute("CREATE VIEW vb AS SELECT tb.id, tb.v, ub.w FROM tb JOIN ub ON tb.id = ub.id");
        engine.execute("CREATE STREAM sb ON VIEW vb");
        engine.execute("CREATE TEMPORARY TABLE tb CHANGE_TRACKING = TRUE AS SELECT 1 AS id, 'c1'::STRING AS v");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('sb')");
        assertAnswer("1|c1|u1|INSERT", "SELECT id, v, w, METADATA$ACTION FROM sb ORDER BY 1");
        engine.execute("UPDATE tb SET v = 'z' WHERE id = 5");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('sb')");
        engine.execute("CREATE TABLE tc (id INT, v STRING)");
        engine.execute("CREATE TABLE uc (id INT, w STRING)");
        engine.execute("INSERT INTO uc VALUES (1, 'u1')");
        engine.execute("CREATE VIEW vc AS SELECT tc.id, tc.v, uc.w FROM tc JOIN uc ON tc.id = uc.id");
        engine.execute("CREATE STREAM sc ON VIEW vc");
        engine.execute("CREATE TEMPORARY TABLE tc (id INT, v STRING) CHANGE_TRACKING = TRUE");
        engine.execute("UPDATE tc SET v = 'z' WHERE id = 5");
        assertAnswer("false", "SELECT SYSTEM$STREAM_HAS_DATA('sc')");
        engine.execute("DELETE FROM tc WHERE id = 5");
        assertAnswer("false", "SELECT SYSTEM$STREAM_HAS_DATA('sc')");
        engine.execute("INSERT INTO tc VALUES (1, 'a')");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('sc')");
        engine.execute("CREATE TABLE sinkc (id INT)");
        engine.execute("INSERT INTO sinkc SELECT id FROM sc");
        assertAnswer("false", "SELECT SYSTEM$STREAM_HAS_DATA('sc')");
        assertAnswer("", "SELECT id, v, w, METADATA$ACTION FROM sc ORDER BY 1");
        engine.execute("DELETE FROM tc");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('sc')");
        assertAnswer("1|a|u1|DELETE", "SELECT id, v, w, METADATA$ACTION FROM sc ORDER BY 1");
        engine.execute("CREATE TABLE td (id INT, v STRING)");
        engine.execute("CREATE TABLE ud (id INT, w STRING)");
        engine.execute("INSERT INTO ud VALUES (1, 'u1')");
        engine.execute("CREATE VIEW vd AS SELECT td.id, td.v, ud.w FROM td JOIN ud ON td.id = ud.id");
        engine.execute("CREATE STREAM sd ON VIEW vd");
        engine.execute("CREATE TEMPORARY TABLE td (id INT, v STRING)");
        engine.execute("INSERT INTO td VALUES (1, 'early')");
        engine.execute("ALTER TABLE td SET CHANGE_TRACKING = TRUE");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('sd')");
        assertAnswer("1|early|u1|INSERT", "SELECT id, v, w, METADATA$ACTION FROM sd ORDER BY 1");
    }
}
