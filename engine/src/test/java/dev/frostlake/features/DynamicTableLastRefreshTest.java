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
 * What a dynamic table answers and how its refresh mode is settled (all live-verified): a read answers the rows of
 * the last refresh, not what the query would read now, until a manual refresh; a FULL refresh that finds no new data
 * keeps its rows, a function whose value moves included, and is NO_DATA in the refresh history, where an AUTO table
 * that settled on FULL is FULL; REFRESH_MODE = INCREMENTAL is refused a query its incremental refresh does not
 * follow, in the account's words and at its places, and AUTO names the same reasons, through the views the query
 * reads.
 */
public class DynamicTableLastRefreshTest extends MeasuredCellsTestSupport {

    @Test
    public void readsLastRefresh() {
        engine.execute("SET lr_t = CURRENT_TIMESTAMP()");
        engine.execute("CREATE OR REPLACE TABLE lr_base (a INT)");
        engine.execute("INSERT INTO lr_base VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE lr_rbase (a INT)");
        engine.execute("INSERT INTO lr_rbase VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE lr_snap (a INT, r INT)");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE lr_dt TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS SELECT "
            + "a FROM lr_base");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE lr_rand TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS "
            + "SELECT a, RANDOM() r FROM lr_rbase");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE lr_lim TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS SELECT"
            + " a FROM lr_rbase ORDER BY a LIMIT 2");
        engine.execute("CREATE OR REPLACE VIEW lr_vs AS SELECT a FROM lr_base WHERE EXISTS (SELECT 1 FROM lr_base)");
        engine.execute("CREATE OR REPLACE VIEW lr_vl AS SELECT a FROM lr_base LIMIT 1");
        engine.execute("CREATE OR REPLACE VIEW lr_vr AS SELECT a, RANDOM() r FROM lr_base");
        engine.execute("CREATE OR REPLACE VIEW lr_vc AS SELECT a, CURRENT_DATE() d FROM lr_base");
        engine.execute("CREATE OR REPLACE VIEW lr_vv AS SELECT a FROM lr_vc");
        assertAnswer("1",
            "SELECT COUNT(*) FROM lr_dt");
        engine.execute("INSERT INTO lr_base VALUES (2)");
        assertAnswer("1",
            "SELECT LISTAGG(a, ',') WITHIN GROUP (ORDER BY a) FROM lr_dt");
        assertAnswer("1",
            "SHOW DYNAMIC TABLES LIKE 'LR_DT' ->> SELECT \"rows\" FROM $1");
        assertAnswer("ADAPTIVE",
            "SHOW DYNAMIC TABLES LIKE 'LR_DT' ->> SELECT \"refresh_mode\" FROM $1");
        assertAnswer("refreshed",
            "EXECUTE IMMEDIATE $$ BEGIN ALTER DYNAMIC TABLE lr_dt REFRESH; RETURN 'refreshed'; END; $$");
        assertAnswer("1,2",
            "SELECT LISTAGG(a, ',') WITHIN GROUP (ORDER BY a) FROM lr_dt");
        engine.execute("DELETE FROM lr_base WHERE a = 1");
        assertAnswer("1,2",
            "SELECT LISTAGG(a, ',') WITHIN GROUP (ORDER BY a) FROM lr_dt");
        assertAnswer("3",
            "SELECT (SELECT COUNT(*) FROM lr_dt) + COUNT(*) FROM lr_base");
        engine.execute("INSERT INTO lr_snap SELECT a, 0 FROM lr_dt");
        assertAnswer("1,2",
            "SELECT LISTAGG(a, ',') WITHIN GROUP (ORDER BY a) FROM lr_snap");
        assertAnswer("suspended",
            "EXECUTE IMMEDIATE $$ BEGIN ALTER DYNAMIC TABLE lr_dt SUSPEND; RETURN 'suspended'; END; $$");
        assertAnswer("1,2",
            "SELECT LISTAGG(a, ',') WITHIN GROUP (ORDER BY a) FROM lr_dt");
        assertAnswer("refreshed",
            "EXECUTE IMMEDIATE $$ BEGIN ALTER DYNAMIC TABLE lr_dt REFRESH; RETURN 'refreshed'; END; $$");
        assertAnswer("2",
            "SELECT LISTAGG(a, ',') WITHIN GROUP (ORDER BY a) FROM lr_dt");
        assertAnswer("INCREMENTAL/CREATION,INCREMENTAL/MANUAL,INCREMENTAL/MANUAL",
            "SELECT LISTAGG(refresh_action || '/' || refresh_trigger, ',') WITHIN GROUP (ORDER BY "
            + "data_timestamp) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => "
            + "'LR_DT')) WHERE refresh_start_time >= $lr_t");
        assertAnswer("0",
            "SELECT COUNT(*) FROM (SELECT a, r FROM lr_rand MINUS SELECT a, r FROM lr_rand)");
        engine.execute("DELETE FROM lr_snap");
        engine.execute("INSERT INTO lr_snap SELECT a, r FROM lr_rand");
        assertAnswer("0",
            "SELECT COUNT(*) FROM (SELECT a, r FROM lr_rand MINUS SELECT a, r FROM lr_snap)");
        assertAnswer("FULL",
            "SHOW DYNAMIC TABLES LIKE 'LR_RAND' ->> SELECT \"refresh_mode\" FROM $1");
        assertAnswer("refreshed",
            "EXECUTE IMMEDIATE $$ BEGIN ALTER DYNAMIC TABLE lr_rand REFRESH; RETURN 'refreshed'; END; $$");
        assertAnswer("0",
            "SELECT COUNT(*) FROM (SELECT a, r FROM lr_rand MINUS SELECT a, r FROM lr_snap)");
        assertAnswer("FULL/CREATION,NO_DATA/MANUAL",
            "SELECT LISTAGG(refresh_action || '/' || refresh_trigger, ',') WITHIN GROUP (ORDER BY "
            + "data_timestamp) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => "
            + "'LR_RAND')) WHERE refresh_start_time >= $lr_t");
        engine.execute("INSERT INTO lr_rbase VALUES (4)");
        assertAnswer("refreshed",
            "EXECUTE IMMEDIATE $$ BEGIN ALTER DYNAMIC TABLE lr_rand REFRESH; RETURN 'refreshed'; END; $$");
        assertAnswer("4",
            "SELECT COUNT(*) FROM lr_rand");
        assertAnswer("FULL/CREATION,NO_DATA/MANUAL,FULL/MANUAL",
            "SELECT LISTAGG(refresh_action || '/' || refresh_trigger, ',') WITHIN GROUP (ORDER BY "
            + "data_timestamp) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => "
            + "'LR_RAND')) WHERE refresh_start_time >= $lr_t");
        assertAnswer("refreshed",
            "EXECUTE IMMEDIATE $$ BEGIN ALTER DYNAMIC TABLE lr_lim REFRESH; RETURN 'refreshed'; END; $$");
        assertAnswer("FULL/CREATION,FULL/MANUAL",
            "SELECT LISTAGG(refresh_action || '/' || refresh_trigger, ',') WITHIN GROUP (ORDER BY "
            + "data_timestamp) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => "
            + "'LR_LIM')) WHERE refresh_start_time >= $lr_t");
        assertAnswer("1,2",
            "SELECT LISTAGG(a, ',') WITHIN GROUP (ORDER BY a) FROM lr_lim");
        assertAnswer("FULL",
            "SHOW DYNAMIC TABLES LIKE 'LR_LIM' ->> SELECT \"refresh_mode\" FROM $1");
        assertAnswer("AUTO",
            "SHOW DYNAMIC TABLES LIKE 'LR_LIM' ->> SELECT \"configured_refresh_mode\" FROM $1");
    }

    @Test
    public void incrementalRefusals() {
        engine.execute("SET lr_t = CURRENT_TIMESTAMP()");
        engine.execute("CREATE OR REPLACE TABLE lr_base (a INT)");
        engine.execute("INSERT INTO lr_base VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE lr_rbase (a INT)");
        engine.execute("INSERT INTO lr_rbase VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE lr_snap (a INT, r INT)");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE lr_dt TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS SELECT "
            + "a FROM lr_base");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE lr_rand TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS "
            + "SELECT a, RANDOM() r FROM lr_rbase");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE lr_lim TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS SELECT"
            + " a FROM lr_rbase ORDER BY a LIMIT 2");
        engine.execute("CREATE OR REPLACE VIEW lr_vs AS SELECT a FROM lr_base WHERE EXISTS (SELECT 1 FROM lr_base)");
        engine.execute("CREATE OR REPLACE VIEW lr_vl AS SELECT a FROM lr_base LIMIT 1");
        engine.execute("CREATE OR REPLACE VIEW lr_vr AS SELECT a, RANDOM() r FROM lr_base");
        engine.execute("CREATE OR REPLACE VIEW lr_vc AS SELECT a, CURRENT_DATE() d FROM lr_base");
        engine.execute("CREATE OR REPLACE VIEW lr_vv AS SELECT a FROM lr_vc");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 123 | Invalid Dynamic Table "
            + "definition. Non-deterministic function 'RANDOM' not allowed in definition. For more "
            + "information, see "
            + "https://docs.snowflake.com/user-guide/dynamic-tables-supported-queries#supported-non-deterministic-functions.",
            "CREATE OR REPLACE DYNAMIC TABLE lr_inc TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT a, RANDOM() r FROM lr_base");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
            + "queries with LIMIT.",
            "CREATE OR REPLACE DYNAMIC TABLE lr_inc TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT a FROM lr_base LIMIT 1");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 141: | Change tracking is not supported on"
            + " queries with subquery expressions.",
            "CREATE OR REPLACE DYNAMIC TABLE lr_inc TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT a FROM lr_base WHERE EXISTS (SELECT 1 FROM lr_base)");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 123: | Query contains the function "
            + "'CURRENT_TIMESTAMP', but change tracking is not supported on queries with non-deterministic "
            + "functions. For more information, see "
            + "https://docs.snowflake.com/user-guide/dynamic-tables-supported-queries#supported-non-deterministic-functions.",
            "CREATE OR REPLACE DYNAMIC TABLE lr_inc TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT a, CURRENT_TIMESTAMP() ts FROM lr_base");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 123 | Invalid Dynamic Table "
            + "definition. Non-deterministic function 'UUID_STRING' not allowed in definition. For more "
            + "information, see "
            + "https://docs.snowflake.com/user-guide/dynamic-tables-supported-queries#supported-non-deterministic-functions.",
            "CREATE OR REPLACE DYNAMIC TABLE lr_inc TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT a, UUID_STRING() u FROM lr_base");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 123: | Query contains the function 'SEQ4',"
            + " but change tracking is not supported on queries with non-deterministic functions. For more "
            + "information, see "
            + "https://docs.snowflake.com/user-guide/dynamic-tables-supported-queries#supported-non-deterministic-functions.",
            "CREATE OR REPLACE DYNAMIC TABLE lr_inc TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT a, SEQ4() s FROM lr_base");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
            + "queries with subquery expressions.",
            "CREATE OR REPLACE DYNAMIC TABLE lr_inc TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT a FROM lr_base WHERE a IN (SELECT a FROM lr_base)");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 121: | Change tracking is not supported on"
            + " queries with subquery expressions.",
            "CREATE OR REPLACE DYNAMIC TABLE lr_inc TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT (SELECT MAX(a) FROM lr_base) m, a FROM lr_base");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 145: | Change tracking is not supported on"
            + " queries with subquery expressions.",
            "CREATE OR REPLACE DYNAMIC TABLE lr_inc TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT a FROM lr_base WHERE a = ANY (SELECT a FROM lr_base)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 138 | Invalid Dynamic Table "
            + "definition. Non-deterministic function 'RANDOM' not allowed in definition. For more "
            + "information, see "
            + "https://docs.snowflake.com/user-guide/dynamic-tables-supported-queries#supported-non-deterministic-functions.",
            "CREATE OR REPLACE DYNAMIC TABLE lr_inc TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT a, UNIFORM(1, 10, RANDOM()) u FROM lr_base");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 141 | Invalid Dynamic Table "
            + "definition. Non-deterministic function 'RANDOM' not allowed in definition. For more "
            + "information, see "
            + "https://docs.snowflake.com/user-guide/dynamic-tables-supported-queries#supported-non-deterministic-functions.",
            "CREATE OR REPLACE DYNAMIC TABLE lr_inc TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT a, CURRENT_DATE() d, RANDOM() r FROM lr_base LIMIT 1");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 60: | Change tracking is not supported on "
            + "queries with subquery expressions. (inside view 'TEST_DB.TEST_SCHEMA.LR_VS').",
            "CREATE OR REPLACE DYNAMIC TABLE lr_inc TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT a FROM lr_vs");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
            + "queries with LIMIT. (inside view 'TEST_DB.TEST_SCHEMA.LR_VL').",
            "CREATE OR REPLACE DYNAMIC TABLE lr_inc TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT a FROM lr_vl");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 42 | Invalid Dynamic Table "
            + "definition. Non-deterministic function 'RANDOM' from view 'LR_VR' not allowed in definition."
            + " For more information, see "
            + "https://docs.snowflake.com/user-guide/dynamic-tables-supported-queries#supported-non-deterministic-functions.",
            "CREATE OR REPLACE DYNAMIC TABLE lr_inc TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT a FROM lr_vr");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 42: | Query contains the function "
            + "'CURRENT_DATE' from view 'LR_VV', but change tracking is not supported on queries with "
            + "non-deterministic functions. For more information, see "
            + "https://docs.snowflake.com/user-guide/dynamic-tables-supported-queries#supported-non-deterministic-functions."
            + " (inside view 'TEST_DB.TEST_SCHEMA.LR_VC').",
            "CREATE OR REPLACE DYNAMIC TABLE lr_inc TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT a FROM lr_vv");
        assertAnswer("REFUSED SQL compilation error: error line 3 at position 2 | Invalid Dynamic Table "
            + "definition. Non-deterministic function 'RANDOM' not allowed in definition. For more "
            + "information, see "
            + "https://docs.snowflake.com/user-guide/dynamic-tables-supported-queries#supported-non-deterministic-functions.",
            "CREATE OR REPLACE DYNAMIC TABLE lr_inc TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL\nAS SELECT a,\n  RANDOM() r FROM lr_base");
        assertAnswer("REFUSED SQL compilation error: line 3 at position 2: | Change tracking is not supported on "
            + "queries with subquery expressions.",
            "CREATE OR REPLACE DYNAMIC TABLE lr_inc TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT a FROM lr_base\n WHERE\n  EXISTS (SELECT 1 FROM "
            + "lr_base)");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 35: | Query contains the function "
            + "'CURRENT_TIMESTAMP', but change tracking is not supported on queries with non-deterministic "
            + "functions. For more information, see "
            + "https://docs.snowflake.com/user-guide/dynamic-tables-supported-queries#supported-non-deterministic-functions.",
            "CREATE OR REPLACE DYNAMIC TABLE lr_inc TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT a, SYSDATE() s FROM lr_base");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 1: | Query contains the function "
            + "'CURRENT_TIMESTAMP', but change tracking is not supported on queries with non-deterministic "
            + "functions. For more information, see "
            + "https://docs.snowflake.com/user-guide/dynamic-tables-supported-queries#supported-non-deterministic-functions.",
            "CREATE OR REPLACE DYNAMIC TABLE lr_inc TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT a, GETDATE() s FROM lr_base");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE lr_inc TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT a, UUID_STRING('fe971b24-9572-4005-b22f-351e9c09274d', "
            + "'x') u FROM lr_base");
        assertAnswer("INCREMENTAL",
            "SHOW DYNAMIC TABLES LIKE 'LR_INC' ->> SELECT \"refresh_mode\" FROM $1");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE lr_inc TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT a, ROW_NUMBER() OVER (ORDER BY a) rn FROM lr_base");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE lr_inc TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL AS SELECT COUNT(*) c FROM lr_base");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
            + "queries with LIMIT.",
            "CREATE OR REPLACE DYNAMIC TABLE lr_inc TARGET_LAG = '1 day' WAREHOUSE = compute_wh "
            + "REFRESH_MODE = INCREMENTAL INITIALIZE = ON_SCHEDULE AS SELECT a FROM lr_base LIMIT 1");
    }

    @Test
    public void autoReasons() {
        engine.execute("SET lr_t = CURRENT_TIMESTAMP()");
        engine.execute("CREATE OR REPLACE TABLE lr_base (a INT)");
        engine.execute("INSERT INTO lr_base VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE lr_rbase (a INT)");
        engine.execute("INSERT INTO lr_rbase VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE lr_snap (a INT, r INT)");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE lr_dt TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS SELECT "
            + "a FROM lr_base");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE lr_rand TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS "
            + "SELECT a, RANDOM() r FROM lr_rbase");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE lr_lim TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS SELECT"
            + " a FROM lr_rbase ORDER BY a LIMIT 2");
        engine.execute("CREATE OR REPLACE VIEW lr_vs AS SELECT a FROM lr_base WHERE EXISTS (SELECT 1 FROM lr_base)");
        engine.execute("CREATE OR REPLACE VIEW lr_vl AS SELECT a FROM lr_base LIMIT 1");
        engine.execute("CREATE OR REPLACE VIEW lr_vr AS SELECT a, RANDOM() r FROM lr_base");
        engine.execute("CREATE OR REPLACE VIEW lr_vc AS SELECT a, CURRENT_DATE() d FROM lr_base");
        engine.execute("CREATE OR REPLACE VIEW lr_vv AS SELECT a FROM lr_vc");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE lr_auto TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS "
            + "SELECT a FROM lr_vv");
        assertAnswer("Query contains the function 'CURRENT_DATE' from view 'LR_VV', but change tracking is not "
            + "supported on queries with non-deterministic functions. For more information, see "
            + "https://docs.snowflake.com/user-guide/dynamic-tables-supported-queries#supported-non-deterministic-functions."
            + " (inside view 'TEST_DB.TEST_SCHEMA.LR_VC').",
            "SHOW DYNAMIC TABLES LIKE 'LR_AUTO' ->> SELECT \"refresh_mode_reason\" FROM $1");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE lr_auto TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS "
            + "SELECT a FROM lr_vs");
        assertAnswer("Change tracking is not supported on queries with subquery expressions. (inside view "
            + "'TEST_DB.TEST_SCHEMA.LR_VS').",
            "SHOW DYNAMIC TABLES LIKE 'LR_AUTO' ->> SELECT \"refresh_mode_reason\" FROM $1");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE lr_auto TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS "
            + "SELECT a, SEQ8() s FROM lr_base LIMIT 1");
        assertAnswer("Change tracking is not supported on queries with LIMIT.",
            "SHOW DYNAMIC TABLES LIKE 'LR_AUTO' ->> SELECT \"refresh_mode_reason\" FROM $1");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE lr_auto TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS "
            + "SELECT a, UNIFORM(1, 10, RANDOM()) u FROM lr_base");
        assertAnswer("Query contains the function 'UNIFORM', but change tracking is not supported on queries with "
            + "non-deterministic functions. For more information, see "
            + "https://docs.snowflake.com/user-guide/dynamic-tables-supported-queries#supported-non-deterministic-functions.",
            "SHOW DYNAMIC TABLES LIKE 'LR_AUTO' ->> SELECT \"refresh_mode_reason\" FROM $1");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE lr_auto TARGET_LAG = '1 day' WAREHOUSE = compute_wh AS "
            + "SELECT a FROM lr_vr");
        assertAnswer("Query contains the function 'RANDOM' from view 'LR_VR', but change tracking is not supported"
            + " on queries with non-deterministic functions. For more information, see "
            + "https://docs.snowflake.com/user-guide/dynamic-tables-supported-queries#supported-non-deterministic-functions."
            + " (inside view 'TEST_DB.TEST_SCHEMA.LR_VR').",
            "SHOW DYNAMIC TABLES LIKE 'LR_AUTO' ->> SELECT \"refresh_mode_reason\" FROM $1");
        assertAnswer("FULL/CREATION,FULL/CREATION,FULL/CREATION,FULL/CREATION,FULL/CREATION",
            "SELECT LISTAGG(refresh_action || '/' || refresh_trigger, ',') WITHIN GROUP (ORDER BY "
            + "data_timestamp) FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME => "
            + "'LR_AUTO')) WHERE refresh_start_time >= $lr_t");
        engine.execute("DROP DYNAMIC TABLE IF EXISTS lr_auto");
        engine.execute("DROP DYNAMIC TABLE IF EXISTS lr_inc");
        engine.execute("DROP DYNAMIC TABLE IF EXISTS lr_lim");
        engine.execute("DROP DYNAMIC TABLE IF EXISTS lr_rand");
        engine.execute("DROP DYNAMIC TABLE IF EXISTS lr_dt");
    }
}
