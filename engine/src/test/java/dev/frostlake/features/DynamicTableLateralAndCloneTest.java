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
 * A LATERAL join to a subquery in a dynamic table's query: REFRESH_MODE = INCREMENTAL is refused it, ahead of what the
 * SELECT holds beside it, through derived tables, common table expressions and views, and AUTO settles on FULL for it;
 * LATERAL FLATTEN is followed. A clone of a dynamic table holds the rows of its source's last refresh, its next
 * refresh counting what changed since, and re-prints its source's CREATE under its own name (all live-verified).
 */
public class DynamicTableLateralAndCloneTest extends MeasuredCellsTestSupport {

    @Test
    public void incrementalRefusesALateralJoin() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t VALUES (1, 10), (2, 20)");
        engine.execute("CREATE OR REPLACE TABLE u (a INT, c INT)");
        engine.execute("INSERT INTO u VALUES (1, 100), (2, 200), (2, 201)");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported"
                + " on queries with joins of type '[LATERAL]'.",
            "CREATE OR REPLACE DYNAMIC TABLE l1 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT t.a, s.c FROM t, LATERAL (SELECT u.c FROM u WHERE "
                + "u.a = t.a) s");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported"
                + " on queries with joins of type '[LATERAL]'.",
            "CREATE OR REPLACE DYNAMIC TABLE l2 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT t.a, s.c FROM t JOIN LATERAL (SELECT u.c FROM u "
                + "WHERE u.a = t.a) s");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported"
                + " on queries with joins of type '[LATERAL]'.",
            "CREATE OR REPLACE DYNAMIC TABLE l3 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT t.a, s.c, CURRENT_DATE() d FROM t, LATERAL (SELECT "
                + "u.c FROM u WHERE u.a = t.a) s");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported"
                + " on queries with LIMIT.",
            "CREATE OR REPLACE DYNAMIC TABLE l5 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT t.a, s.c FROM t, LATERAL (SELECT u.c FROM u WHERE "
                + "u.a = t.a) s LIMIT 5");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 126 | Invalid Dynamic Table "
                + "definition. Non-deterministic function 'RANDOM' not allowed in definition. For more "
                + "information, see "
                + "https://docs.snowflake.com/user-guide/dynamic-tables-supported-queries#supported-non-deterministic-functions.",
            "CREATE OR REPLACE DYNAMIC TABLE l6 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT t.a, s.c, RANDOM() r FROM t, LATERAL (SELECT u.c "
                + "FROM u WHERE u.a = t.a) s");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported"
                + " on queries with joins of type '[LATERAL]'.",
            "CREATE OR REPLACE DYNAMIC TABLE l7 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT t.a, (SELECT MAX(c) FROM u) m, s.c FROM t, LATERAL "
                + "(SELECT u.c FROM u WHERE u.a = t.a) s");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported"
                + " on queries with joins of type '[LATERAL]'.",
            "CREATE OR REPLACE DYNAMIC TABLE l8 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT t.a, s.c FROM t, LATERAL (SELECT u.c FROM u WHERE "
                + "u.a = t.a) s WHERE EXISTS (SELECT 1 FROM u)");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported"
                + " on queries with joins of type '[LATERAL]'.",
            "CREATE OR REPLACE DYNAMIC TABLE l9 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT t.a, s.c FROM t, LATERAL (SELECT u.c, "
                + "CURRENT_DATE() d FROM u WHERE u.a = t.a) s");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE l10 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT t.a, f.value v FROM t, LATERAL FLATTEN(INPUT => "
            + "ARRAY_CONSTRUCT(t.a, t.b)) f");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported"
                + " on queries with joins of type '[LATERAL]'.",
            "CREATE OR REPLACE DYNAMIC TABLE l11 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT t.a, s.c FROM t LEFT JOIN LATERAL (SELECT u.c FROM "
                + "u WHERE u.a = t.a) s");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported"
                + " on queries with joins of type '[LATERAL]'.",
            "CREATE OR REPLACE DYNAMIC TABLE l12 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT t.a, s.c FROM t, LATERAL (SELECT u.c FROM u WHERE "
                + "u.a = t.a) s, LATERAL (SELECT 1 x) s2");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported"
                + " on queries with joins of type '[LATERAL]'.",
            "CREATE OR REPLACE DYNAMIC TABLE l14 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT a, c FROM (SELECT t.a, s.c FROM t, LATERAL (SELECT "
                + "u.c FROM u WHERE u.a = t.a) s)");
        engine.execute("CREATE OR REPLACE VIEW v AS SELECT t.a, s.c FROM t, LATERAL (SELECT u.c FROM u WHERE u.a"
            + " = t.a) s");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported"
                + " on queries with joins of type '[LATERAL]'. (inside view 'TEST_DB.TEST_SCHEMA.V').",
            "CREATE OR REPLACE DYNAMIC TABLE lv TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT a, c FROM v");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE a1 TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS SELECT"
            + " t.a, s.c FROM t, LATERAL (SELECT u.c FROM u WHERE u.a = t.a) s");
        assertAnswer("FULL|Change tracking is not supported on queries with joins of type '[LATERAL]'.",
            "SHOW DYNAMIC TABLES LIKE 'A1' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM "
                + "$1");
        assertAnswer("1|100 ;; 2|200 ;; 2|201",
            "SELECT a, c FROM a1 ORDER BY a, c");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE a2 TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS SELECT"
            + " t.a, s.c, CURRENT_DATE() d FROM t, LATERAL (SELECT u.c FROM u WHERE u.a = t.a) s");
        assertAnswer("FULL|Change tracking is not supported on queries with joins of type '[LATERAL]'.",
            "SHOW DYNAMIC TABLES LIKE 'A2' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM "
                + "$1");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE a4 TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS SELECT"
            + " t.a, s.c FROM t, LATERAL (SELECT u.c FROM u WHERE u.a = t.a) s LIMIT 5");
        assertAnswer("FULL|Change tracking is not supported on queries with LIMIT.",
            "SHOW DYNAMIC TABLES LIKE 'A4' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM "
                + "$1");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE a5 TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS SELECT"
            + " COUNT(*) n FROM t, LATERAL (SELECT u.c FROM u WHERE u.a = t.a) s");
        assertAnswer("FULL|Change tracking is not supported on queries with joins of type '[LATERAL]'.",
            "SHOW DYNAMIC TABLES LIKE 'A5' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM "
                + "$1");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE a6 TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS SELECT"
            + " a, c FROM v");
        assertAnswer("FULL|Change tracking is not supported on queries with joins of type '[LATERAL]'. (inside"
                + " view 'TEST_DB.TEST_SCHEMA.V').",
            "SHOW DYNAMIC TABLES LIKE 'A6' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM "
                + "$1");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE a7 TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS SELECT"
            + " t.a, f.value v FROM t, LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(t.a, t.b)) f");
        assertAnswer("ADAPTIVE|null",
            "SHOW DYNAMIC TABLES LIKE 'A7' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM "
                + "$1");
    }

    @Test
    public void lateralPrecedence() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t VALUES (1, 10), (2, 20)");
        engine.execute("CREATE OR REPLACE TABLE u (a INT, c INT)");
        engine.execute("INSERT INTO u VALUES (1, 100), (2, 200), (2, 201)");
        engine.execute("CREATE OR REPLACE VIEW v AS SELECT t.a, s.c FROM t, LATERAL (SELECT u.c FROM u WHERE u.a"
            + " = t.a) s");
        engine.execute("CREATE OR REPLACE VIEW vl AS SELECT a FROM t LIMIT 1");
        engine.execute("CREATE OR REPLACE VIEW vd AS SELECT a, CURRENT_DATE() d FROM t");
        engine.execute("CREATE OR REPLACE VIEW vr AS SELECT t.a, s.c, RANDOM() r FROM t, LATERAL (SELECT u.c "
            + "FROM u WHERE u.a = t.a) s");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported"
                + " on queries with joins of type '[LATERAL]'.",
            "CREATE OR REPLACE DYNAMIC TABLE n1 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT x.a, s.c FROM (SELECT a FROM t LIMIT 1) x, LATERAL "
                + "(SELECT u.c FROM u WHERE u.a = x.a) s");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported"
                + " on queries with joins of type '[LATERAL]'.",
            "CREATE OR REPLACE DYNAMIC TABLE n2 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT vl.a, s.c FROM vl, LATERAL (SELECT u.c FROM u WHERE"
                + " u.a = vl.a) s");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported"
                + " on queries with joins of type '[LATERAL]'. (inside view 'TEST_DB.TEST_SCHEMA.V').",
            "CREATE OR REPLACE DYNAMIC TABLE n3 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT a, c, CURRENT_DATE() d FROM v");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported"
                + " on queries with joins of type '[LATERAL]'.",
            "CREATE OR REPLACE DYNAMIC TABLE n4 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT vd.a, vd.d, s.c FROM vd, LATERAL (SELECT u.c FROM u"
                + " WHERE u.a = vd.a) s");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 46 | Invalid Dynamic Table "
                + "definition. Non-deterministic function 'RANDOM' from view 'VR' not allowed in "
                + "definition. For more information, see "
                + "https://docs.snowflake.com/user-guide/dynamic-tables-supported-queries#supported-non-deterministic-functions.",
            "CREATE OR REPLACE DYNAMIC TABLE n5 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT a, c FROM vr");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported"
                + " on queries with joins of type '[LATERAL]'.",
            "CREATE OR REPLACE DYNAMIC TABLE n6 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT a FROM t UNION ALL SELECT t.a FROM t, LATERAL "
                + "(SELECT u.c FROM u WHERE u.a = t.a) s");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported"
                + " on queries with subquery expressions.",
            "CREATE OR REPLACE DYNAMIC TABLE n7 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT a FROM t WHERE a IN (SELECT a FROM u) UNION ALL "
                + "SELECT t.a FROM t, LATERAL (SELECT u.c FROM u WHERE u.a = t.a) s");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported"
                + " on queries with joins of type '[LATERAL]'.",
            "CREATE OR REPLACE DYNAMIC TABLE n8 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS WITH w AS (SELECT t.a, s.c FROM t, LATERAL (SELECT u.c "
                + "FROM u WHERE u.a = t.a) s) SELECT a, c FROM w");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported"
                + " on queries with joins of type '[LATERAL]'.",
            "CREATE OR REPLACE DYNAMIC TABLE n9 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT t.a, s.c FROM t, LATERAL (SELECT u.c FROM u WHERE "
                + "u.a = t.a) s, LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(1)) f");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported"
                + " on queries with LIMIT.",
            "CREATE OR REPLACE DYNAMIC TABLE n10 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT a, c FROM v LIMIT 3");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported"
                + " on queries with joins of type '[LATERAL]'.",
            "CREATE OR REPLACE DYNAMIC TABLE n11 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT t.a, s.x FROM t, LATERAL (SELECT t.b + 1 x) s");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported"
                + " on queries with joins of type '[LATERAL]'.",
            "CREATE OR REPLACE DYNAMIC TABLE n12 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT t.a, s.c FROM t, LATERAL (SELECT u.c FROM u) s");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE b1 TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS SELECT"
            + " a, c, CURRENT_DATE() d FROM v");
        assertAnswer("FULL|Change tracking is not supported on queries with joins of type '[LATERAL]'. (inside"
                + " view 'TEST_DB.TEST_SCHEMA.V').",
            "SHOW DYNAMIC TABLES LIKE 'B1' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM "
                + "$1");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE b2 TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS SELECT"
            + " a, SUM(c) OVER () s FROM v");
        assertAnswer("FULL|Change tracking is not supported on queries with joins of type '[LATERAL]'. (inside"
                + " view 'TEST_DB.TEST_SCHEMA.V').",
            "SHOW DYNAMIC TABLES LIKE 'B2' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM "
                + "$1");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE b3 TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS SELECT"
            + " a FROM t WHERE a IN (SELECT a FROM u) UNION ALL SELECT t.a FROM t, LATERAL (SELECT u.c "
            + "FROM u WHERE u.a = t.a) s");
        assertAnswer("FULL|Change tracking is not supported on queries with subquery expressions.",
            "SHOW DYNAMIC TABLES LIKE 'B3' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\" FROM "
                + "$1");
    }

    @Test
    public void lateralInsideASubqueryExpression() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t VALUES (1, 10), (2, 20)");
        engine.execute("CREATE OR REPLACE TABLE u (a INT, c INT)");
        engine.execute("INSERT INTO u VALUES (1, 100), (2, 200), (2, 201)");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 133: | Change tracking is not "
                + "supported on queries with subquery expressions.",
            "CREATE OR REPLACE DYNAMIC TABLE e2 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
                + "REFRESH_MODE = INCREMENTAL AS SELECT t.a FROM t WHERE EXISTS (SELECT 1 FROM u, LATERAL "
                + "(SELECT 1 x) s)");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE e4 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT t.a, f.value v FROM t JOIN LATERAL FLATTEN(INPUT =>"
            + " ARRAY_CONSTRUCT(t.a)) f");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE e5 TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT t.a, f.value v FROM t, TABLE(FLATTEN(INPUT => "
            + "ARRAY_CONSTRUCT(t.a))) f");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE f TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS SELECT "
            + "a FROM t LIMIT 5");
    }

    @Test
    public void aCloneHoldsItsSourcesLastRefresh() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE d TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS SELECT "
            + "a FROM t");
        engine.execute("INSERT INTO t VALUES (3)");
        assertAnswer("1 ;; 2",
            "SELECT a FROM d ORDER BY a");
        engine.execute("CREATE DYNAMIC TABLE d2 CLONE d");
        assertAnswer("1 ;; 2",
            "SELECT a FROM d2 ORDER BY a");
        assertAnswer("2|ADAPTIVE|SUSPENDED|1 day|CREATE OR REPLACE DYNAMIC TABLE D2 lag = '1 day' refresh_mode"
                + " = 'AUTO' initialize = 'ON_CREATE' warehouse = COMPUTE_WH AS SELECT a FROM t",
            "SHOW DYNAMIC TABLES LIKE 'D2' ->> SELECT \"rows\", \"refresh_mode\", "
                + "\"scheduling_state\", \"target_lag\", \"text\" FROM $1");
        engine.execute("INSERT INTO t VALUES (4)");
        assertAnswer("1 ;; 2",
            "SELECT a FROM d2 ORDER BY a");
        assertAnswer("{\"insertedRows\":2,\"copiedRows\":0,\"deletedRows\":0}",
            "ALTER DYNAMIC TABLE d2 REFRESH ->> SELECT \"statistics\" FROM $1");
        assertAnswer("1 ;; 2 ;; 3 ;; 4",
            "SELECT a FROM d2 ORDER BY a");
        assertAnswer("1 ;; 2",
            "SELECT a FROM d ORDER BY a");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE d3 CLONE d TARGET_LAG = '2 hours'");
        assertAnswer("2|2 hours",
            "SHOW DYNAMIC TABLES LIKE 'D3' ->> SELECT \"rows\", \"target_lag\" FROM $1");
        assertAnswer("1 ;; 2",
            "SELECT a FROM d3 ORDER BY a");
        assertAnswer("REFUSED SQL compilation error: | Invalid operation Cannot clone a dynamic table from a "
                + "table.",
            "CREATE DYNAMIC TABLE d4 CLONE t");
    }

    @Test
    public void aCloneReprintsItsSourcesCreate() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1), (2)");
        engine.execute("create or replace dynamic table d target_lag = '1 day' warehouse = compute_wh as select "
            + "a from t");
        assertAnswer("create or replace dynamic table d lag = '1 day' refresh_mode = 'AUTO' initialize = "
                + "'ON_CREATE' warehouse = COMPUTE_WH as select a from t",
            "SHOW DYNAMIC TABLES LIKE 'D' ->> SELECT \"text\" FROM $1");
        engine.execute("CREATE DYNAMIC TABLE \"d2x\" CLONE d");
        assertAnswer("create or replace dynamic table \"d2x\" lag = '1 day' refresh_mode = 'AUTO' initialize ="
                + " 'ON_CREATE' warehouse = COMPUTE_WH as select a from t|2",
            "SHOW DYNAMIC TABLES LIKE 'd2x' ->> SELECT \"text\", \"rows\" FROM $1");
        engine.execute("CREATE DYNAMIC TABLE d3 CLONE d WAREHOUSE = compute_wh TARGET_LAG = DOWNSTREAM");
        assertAnswer("create or replace dynamic table D3 lag = 'DOWNSTREAM' refresh_mode = 'AUTO' initialize ="
                + " 'ON_CREATE' warehouse = COMPUTE_WH as select a from t|DOWNSTREAM",
            "SHOW DYNAMIC TABLES LIKE 'D3' ->> SELECT \"text\", \"target_lag\" FROM $1");
        assertAnswer("create or replace dynamic table D3( | 	A | ) target_lag = 'DOWNSTREAM' refresh_mode = "
                + "AUTO initialize = ON_CREATE warehouse = COMPUTE_WH |  as select a from t;",
            "SELECT GET_DDL('TABLE', 'D3')");
        assertAnswer("0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => "
                + "'D3'))");
        engine.execute("INSERT INTO t VALUES (3)");
        assertAnswer("{\"insertedRows\":1,\"copiedRows\":0,\"deletedRows\":0}",
            "ALTER DYNAMIC TABLE d3 REFRESH ->> SELECT \"statistics\" FROM $1");
        assertAnswer("1 ;; 2 ;; 3",
            "SELECT a FROM d3 ORDER BY a");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE e TARGET_LAG = '1 day' WAREHOUSE = compute_wh INITIALIZE"
            + " = ON_SCHEDULE AS SELECT a FROM t");
    }

    @Test
    public void aCloneKeepsItsSourcesRefreshMode() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t VALUES (1, 10), (2, 20)");
        engine.execute("CREATE OR REPLACE TABLE u (a INT, c INT)");
        engine.execute("INSERT INTO u VALUES (1, 100), (2, 200), (2, 201)");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE f TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS SELECT "
            + "a FROM t LIMIT 5");
        engine.execute("CREATE DYNAMIC TABLE test_db.test_schema.f2 CLONE test_db.test_schema.f");
        assertAnswer("FULL|Change tracking is not supported on queries with LIMIT.|CREATE OR REPLACE DYNAMIC "
                + "TABLE F2 lag = '1 day' refresh_mode = 'AUTO' initialize = 'ON_CREATE' warehouse = "
                + "COMPUTE_WH AS SELECT a FROM t LIMIT 5",
            "SHOW DYNAMIC TABLES LIKE 'F2' ->> SELECT \"refresh_mode\", \"refresh_mode_reason\", "
                + "\"text\" FROM $1");
    }
}
