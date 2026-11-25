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
 * A dynamic table, an alert and a contact refuse a create over a taken name and a rename onto one with the plain
 * {@code already exists} sentence, and a name another kind of relation holds with its kind; a dynamic table and a
 * contact rename into another schema, while ALTER ALERT and ALTER CORTEX SEARCH SERVICE have no RENAME clause
 * (all live-verified).
 */
public class TakenNameRemainingKindsTest extends MeasuredCellsTestSupport {

    @Test
    public void theTakenNamesReadAsTheAccountReadsThem() {
        engine.execute("CREATE OR REPLACE SCHEMA test_db.tnr_s2");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE TABLE bt (x INT)");
        engine.execute("CREATE DYNAMIC TABLE dt TARGET_LAG = '1 day' WAREHOUSE = COMPUTE_WH AS SELECT x FROM bt");
        engine.execute("CREATE DYNAMIC TABLE dt2 TARGET_LAG = '1 day' WAREHOUSE = COMPUTE_WH AS SELECT x FROM bt");
        engine.execute("CREATE ALERT a1 WAREHOUSE = COMPUTE_WH SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN "
            + "SELECT 1");
        engine.execute("CREATE CONTACT ct1 URL = 'https://example.com'");
        engine.execute("CREATE CONTACT ct2 URL = 'https://example.com'");
        assertCells(new String[][] {
            {"CREATE DYNAMIC TABLE dt TARGET_LAG = '1 day' WAREHOUSE = COMPUTE_WH AS SELECT x FROM bt",
                "REFUSED SQL compilation error: | Object 'DT' already exists."},
            {"CREATE TABLE dt (y INT)",
                "REFUSED SQL compilation error: | Object 'DT' already exists as DYNAMIC_TABLE"},
            {"CREATE DYNAMIC TABLE bt TARGET_LAG = '1 day' WAREHOUSE = COMPUTE_WH AS SELECT x FROM bt",
                "REFUSED SQL compilation error: | Object 'BT' already exists as TABLE"},
            {"ALTER DYNAMIC TABLE dt RENAME TO dt2",
                "REFUSED SQL compilation error: | Object 'DT2' already exists."},
            {"ALTER DYNAMIC TABLE dt RENAME TO bt",
                "REFUSED SQL compilation error: | Object 'BT' already exists."},
            {"ALTER DYNAMIC TABLE dt RENAME TO test_db.tnr_s2.dt_m", "Statement executed successfully."},
            {"CREATE ALERT a1 WAREHOUSE = COMPUTE_WH SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN "
                + "SELECT 1",
                "REFUSED SQL compilation error: | Object 'A1' already exists."},
            {"ALTER ALERT a1 RENAME TO a2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'RENAME'."},
            {"ALTER ALERT nope RENAME TO a9",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'RENAME'."},
            {"CREATE CONTACT ct1 URL = 'https://example.com'",
                "REFUSED SQL compilation error: | Object 'CT1' already exists."},
            {"ALTER CONTACT ct1 RENAME TO ct2",
                "REFUSED SQL compilation error: | Object 'CT2' already exists."},
            {"ALTER CONTACT ct1 RENAME TO test_db.tnr_s2.ct_m", "Statement executed successfully."},
            {"ALTER CORTEX SEARCH SERVICE css RENAME TO css2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'RENAME'."},
        });
        engine.execute("SHOW DYNAMIC TABLES IN DATABASE test_db");
        assertCells(new String[][] {
            {"SELECT \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 2",
                "TEST_SCHEMA|DT2 ;; TNR_S2|DT_M"},
        });
        engine.execute("SHOW CONTACTS IN DATABASE test_db");
        assertCells(new String[][] {
            {"SELECT \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 2",
                "TEST_SCHEMA|CT2 ;; TNR_S2|CT_M"},
        });
    }
}
