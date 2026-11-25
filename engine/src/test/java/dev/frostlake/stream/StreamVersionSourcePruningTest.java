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
 * A zero-row INSERT writes a version of its target as the account compiled its source (live-verified): where it
 * settles the source empty before a row is read - one SELECT over one table that holds no row or whose WHERE its
 * statistics rule out, through derived relations, common table expressions and views that hand the columns on -
 * the version adds and removes no row, and inside a transaction none is written; where it reads the source - a
 * SELECT of no table, a join, a set operation, an ORDER BY, LIMIT, GROUP BY or DISTINCT, a volatile call, a value
 * inside the column's range - the version rewrites the target, adding and removing every row it holds.
 * SYSTEM$STREAM_HAS_DATA follows the rows.
 */
public class StreamVersionSourcePruningTest extends MeasuredCellsTestSupport {

    @Test
    public void zeroRowSources() {
        engine.execute("CREATE OR REPLACE TABLE src (a INT)");
        engine.execute("INSERT INTO src VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE e (a INT)");
        engine.execute("SET v = 99");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT 1 WHERE FALSE");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("true",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE FALSE");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE 1 = 0");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a < 0");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM e");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT src.a FROM src JOIN e ON src.a = e.a");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("true",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT src.a FROM src JOIN src s2 ON src.a = s2.a + 100");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("true",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT 1 HAVING FALSE");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("true",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT COUNT(*) FROM src HAVING COUNT(*) > 100");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("true",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t WITH c AS (SELECT a FROM src WHERE a = 99) SELECT a FROM c");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t WITH c AS (SELECT 1 a WHERE FALSE) SELECT a FROM c");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("true",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a IN (SELECT a FROM e)");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE NOT EXISTS (SELECT 1 FROM src s2 WHERE "
            + "s2.a = src.a)");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("true",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = 99 UNION ALL SELECT a FROM src WHERE a"
            + " = 98");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("true",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM e UNION SELECT a FROM e");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("true",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = $v");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = RANDOM()");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("true",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src LIMIT 0");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("true",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT 1 LIMIT 0");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("true",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT SEQ4() FROM TABLE(GENERATOR(ROWCOUNT => 0))");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("true",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = (SELECT MAX(a) + 1 FROM src)");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT 1 WHERE 1 = (SELECT 2)");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("true",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT OVERWRITE INTO t SELECT a FROM src WHERE a = 99");
        assertAnswer("true",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT ALL INTO t SELECT a FROM src WHERE a = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT ALL INTO t SELECT 1 a WHERE FALSE");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("true",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a > 3");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = 2 AND a = 3");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a + 0 = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a IS NULL");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT column1 FROM VALUES (1) WHERE column1 = 5");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("true",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a::VARCHAR = 'x'");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("true",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = 99 AND RANDOM() > 0");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = 2 AND FALSE");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT OVERWRITE INTO t SELECT 1 WHERE FALSE");
        assertAnswer("true",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a BETWEEN 10 AND 20");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src s WHERE a IN (99, 98)");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = 99 ORDER BY a");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("true",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT MAX(a) FROM src WHERE a = 99 GROUP BY a");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("true",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM (SELECT a FROM src WHERE a = 99)");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("UPDATE t SET a = a WHERE a = 99");
        assertAnswer("UPDATE|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("UPDATE t SET a = 1 WHERE FALSE");
        assertAnswer("UPDATE|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("UPDATE t SET a = 1 WHERE a = RANDOM()");
        assertAnswer("UPDATE|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("DELETE FROM t WHERE a = 99");
        assertAnswer("UPDATE|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("DELETE FROM t WHERE FALSE");
        assertAnswer("UPDATE|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("DELETE FROM t WHERE a = RANDOM()");
        assertAnswer("UPDATE|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT 1 WHERE FALSE");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE FALSE");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE 1 = 0");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a < 0");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM e");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT src.a FROM src JOIN e ON src.a = e.a");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT src.a FROM src JOIN src s2 ON src.a = s2.a + 100");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT 1 HAVING FALSE");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT COUNT(*) FROM src HAVING COUNT(*) > 100");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t WITH c AS (SELECT a FROM src WHERE a = 99) SELECT a FROM c");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t WITH c AS (SELECT 1 a WHERE FALSE) SELECT a FROM c");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a IN (SELECT a FROM e)");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE NOT EXISTS (SELECT 1 FROM src s2 WHERE "
            + "s2.a = src.a)");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = 99 UNION ALL SELECT a FROM src WHERE a"
            + " = 98");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM e UNION SELECT a FROM e");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = $v");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = RANDOM()");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src LIMIT 0");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT 1 LIMIT 0");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT SEQ4() FROM TABLE(GENERATOR(ROWCOUNT => 0))");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = (SELECT MAX(a) + 1 FROM src)");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT 1 WHERE 1 = (SELECT 2)");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT OVERWRITE INTO t SELECT a FROM src WHERE a = 99");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT ALL INTO t SELECT a FROM src WHERE a = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT ALL INTO t SELECT 1 a WHERE FALSE");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a > 3");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = 2 AND a = 3");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a + 0 = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a IS NULL");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT column1 FROM VALUES (1) WHERE column1 = 5");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a::VARCHAR = 'x'");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = 99 AND RANDOM() > 0");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = 2 AND FALSE");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT OVERWRITE INTO t SELECT 1 WHERE FALSE");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a BETWEEN 10 AND 20");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src s WHERE a IN (99, 98)");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = 99 ORDER BY a");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT MAX(a) FROM src WHERE a = 99 GROUP BY a");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM (SELECT a FROM src WHERE a = 99)");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT 1 WHERE FALSE");
        assertAnswer("INSERT|2|2",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("true",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = RANDOM()");
        assertAnswer("INSERT|2|2",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("true",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = (SELECT MAX(a) + 1 FROM src)");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("false",
            "SELECT SYSTEM$STREAM_HAS_DATA('s')");
    }

    @Test
    public void boundaries() {
        engine.execute("CREATE OR REPLACE TABLE s13 (a INT, b VARCHAR)");
        engine.execute("INSERT INTO s13 VALUES (1, 'a'), (3, 'z')");
        engine.execute("CREATE OR REPLACE TABLE s7 (a INT)");
        engine.execute("INSERT INTO s7 VALUES (7)");
        engine.execute("CREATE OR REPLACE TABLE sp (a INT)");
        engine.execute("INSERT INTO sp VALUES (1)");
        engine.execute("INSERT INTO sp VALUES (3)");
        engine.execute("INSERT INTO sp VALUES (5)");
        engine.execute("CREATE OR REPLACE TABLE e (a INT)");
        engine.execute("CREATE OR REPLACE VIEW vw AS SELECT a FROM s13 WHERE a = 99");
        engine.execute("CREATE OR REPLACE TABLE ed (a INT)");
        engine.execute("INSERT INTO ed VALUES (1)");
        engine.execute("DELETE FROM ed");
        engine.execute("SET w = 2");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM s13 WHERE a = 2");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM s13 WHERE a = 1 AND a = 3");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM s13 WHERE a = (SELECT 2)");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM s13 WHERE a IN (SELECT a FROM s13 WHERE a = 99)");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM s13 WHERE a IN (SELECT a FROM s7)");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM s13 WHERE a = $w");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM sp WHERE a = 2");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM sp WHERE a = 4");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT DISTINCT a FROM s13 WHERE a = 99");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a + 1 FROM s13 WHERE a = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM vw");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM s13 WHERE a = 99 LIMIT 5");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM ed");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM s13 WHERE a > 1 AND a < 3");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM s13 WHERE a = 2 OR a = 4");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM s13 WHERE a IN (SELECT a FROM s7 WHERE a = 2)");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM s13 WHERE a = (SELECT MAX(a) FROM s7)");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM s13 WHERE a = (SELECT MAX(a) FROM s13) + 1");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM s13 WHERE a = (SELECT a FROM s7 WHERE a = 2)");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM s13 WHERE b = 'zz'");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM s13 WHERE b = 'm'");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM s13 WHERE a IN (SELECT a FROM e)");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM s13 WHERE EXISTS (SELECT 1 FROM e)");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM s13 WHERE a = 2 AND a = 3");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM s13 x WHERE x.a = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM s13 WHERE a NOT IN (1, 3)");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM s13 WHERE a <> a");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM s13 WHERE a IS NULL AND b IS NULL");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        assertAnswer("",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t (a) SELECT a FROM s13 WHERE a = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
    }

    @Test
    public void transactionsAndMultiTableInserts() {
        engine.execute("CREATE OR REPLACE TABLE src (a INT)");
        engine.execute("INSERT INTO src VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("BEGIN");
        engine.execute("INSERT INTO t SELECT 1 WHERE FALSE");
        engine.execute("COMMIT");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("BEGIN");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = 99");
        engine.execute("COMMIT");
        assertAnswer("",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("BEGIN");
        engine.execute("INSERT INTO t SELECT 1 WHERE FALSE");
        engine.execute("INSERT INTO t VALUES (5)");
        engine.execute("COMMIT");
        assertAnswer("INSERT|1|1 ;; INSERT|2|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE t2 (a INT)");
        engine.execute("INSERT INTO t2 VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("CREATE OR REPLACE STREAM s2 ON TABLE t2");
        engine.execute("INSERT ALL WHEN a > 100 THEN INTO t WHEN a < 100 THEN INTO t2 SELECT a FROM src");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("INSERT|4|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM "
            + "TABLE(SYSTEM$STREAM_BACKLOG('s2')) ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("CREATE OR REPLACE STREAM s2 ON TABLE t2");
        engine.execute("INSERT ALL INTO t INTO t2 SELECT a FROM src WHERE a = RANDOM()");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        assertAnswer("INSERT|4|4",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM "
            + "TABLE(SYSTEM$STREAM_BACKLOG('s2')) ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT FIRST WHEN a > 100 THEN INTO t SELECT a FROM src");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT ALL INTO t INTO t2 SELECT a FROM src WHERE a = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
    }

    @Test
    public void holdouts() {
        engine.execute("CREATE OR REPLACE TABLE src (a INT, b VARCHAR, c INT, d DATE)");
        engine.execute("INSERT INTO src VALUES (1, 'a', NULL, '2020-01-01'), (2, 'c', NULL, "
            + "'2020-02-01'), (3, 'c', NULL, '2020-03-01')");
        engine.execute("CREATE OR REPLACE VIEW vall AS SELECT a FROM src");
        engine.execute("CREATE OR REPLACE TABLE src2 (a INT)");
        engine.execute("INSERT INTO src2 VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE srcn (a INT)");
        engine.execute("INSERT INTO srcn VALUES (1), (NULL), (3)");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = 3");
        assertAnswer("INSERT|2|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a > 2 AND a < 3");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a >= 4");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a <= 0");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE b = 'b'");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE b > 'zz'");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = 99 OR a = 98");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE NOT (a < 99)");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM (SELECT a FROM src) WHERE a = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a IN (SELECT 99)");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE d = '2030-01-01'::DATE");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE c IS NOT NULL");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE c = 5");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM vall WHERE a = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT * FROM src2 WHERE a = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = 1.5");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = '99'");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE ABS(a) = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM srcn WHERE a IS NULL AND a = 7");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = 2 AND b = 'q'");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE 99 = a");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a BETWEEN 4 AND 9");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a IN (1.5, 2.5)");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE d > CURRENT_DATE()");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t WITH c AS (SELECT a FROM src) SELECT a FROM c WHERE a = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE src (a INT, b VARCHAR, d DATE)");
        engine.execute("INSERT INTO src VALUES (1, 'a', '2020-01-01'), (2, 'c', '2020-02-01'), (3, 'c', "
            + "'2020-03-01')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src s WHERE s.a = 99 AND s.b = 'a'");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM (SELECT a, b FROM src WHERE b = 'c') WHERE a = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT x FROM (SELECT a AS x FROM src) WHERE x = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = 2.0 AND b = 'a'");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = -1 * 5");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = (SELECT a FROM src WHERE a = 2) + 10");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a IN (SELECT a + 10 FROM src)");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a IN (SELECT a FROM src) AND a = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE b LIKE 'zz%'");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE b = UPPER('zz')");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE d = CURRENT_DATE()");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = 99 AND a = RANDOM()");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE (a = 99)");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a < 1 OR a > 3");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE b IN ('x', 'y')");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE b IS NULL");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE b = 'b' OR b = 'zz'");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT 5 FROM src WHERE a = 99");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src AS s WHERE s.a > 10");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE b = 'b' AND b = 'q'");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE src (a INT, b VARCHAR)");
        engine.execute("INSERT INTO src VALUES (1, 'a'), (2, 'c'), (3, 'c')");
        engine.execute("CREATE OR REPLACE TABLE src2 (a INT)");
        engine.execute("INSERT INTO src2 VALUES (2)");
        engine.execute("CREATE OR REPLACE TABLE src3 (a INT)");
        engine.execute("INSERT INTO src3 VALUES (2)");
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t (a) VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a, 5 FROM src WHERE a = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t (a) VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT 5, a FROM src WHERE a = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t (a) VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a * 2, a FROM src WHERE a = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t (a) VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT NULL, NULL FROM src WHERE a = 99");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t (a) VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a, a FROM src WHERE a = (SELECT MIN(a) FROM src) - 1");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t (a) VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a, a FROM src WHERE a = (SELECT COUNT(*) FROM src) + 10");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t (a) VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a, a FROM src WHERE a = (SELECT MAX(a) FROM src WHERE a > "
            + "0) + 10");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t (a) VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a, a FROM src WHERE a = (SELECT a FROM src LIMIT 1) + 10");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t (a) VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a, a FROM src WHERE b LIKE 'b%'");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t (a) VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a, a FROM src WHERE b LIKE '%zz'");
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t (a) VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a, a FROM src WHERE b LIKE 'Z%'");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t (a) VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a, a FROM src WHERE b ILIKE 'zz%'");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t (a) VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a, a FROM src WHERE a = (SELECT MAX(a) + 10 FROM src2)");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t (a) VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a, a FROM src WHERE a = (SELECT a FROM src2) + 10");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR)");
        engine.execute("INSERT INTO t (a) VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a, CURRENT_DATE() FROM src3 WHERE a = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t (a) VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a, a FROM src WHERE a = (SELECT 1 + 98)");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE src (a INT, b VARCHAR)");
        engine.execute("INSERT INTO src VALUES (1, 'a'), (2, 'c'), (3, 'c')");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = (SELECT MAX(a) FROM src WHERE a > 1) +"
            + " 10");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT x FROM (SELECT a AS x, b FROM src) WHERE x > 50");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE b LIKE 'c_'");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE b LIKE 'b'");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = (SELECT COUNT(*) FROM src) * 10");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT NULL FROM src WHERE FALSE");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM (SELECT 5 AS a FROM src WHERE a = 99)");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT COALESCE(a, 0) FROM src WHERE a = 99");
        assertAnswer("INSERT|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE b LIKE '%q%'");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t SELECT a FROM src WHERE a = (SELECT MIN(a) FROM src) + 0.5");
        assertAnswer("INSERT|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED FROM TABLE(SYSTEM$STREAM_BACKLOG('s'))"
            + " ORDER BY STREAM_OFFSET_TIME");
    }
}
