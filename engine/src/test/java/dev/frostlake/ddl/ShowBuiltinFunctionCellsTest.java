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
 * A built-in function's SHOW FUNCTIONS row, whichever command lists it: its schema_name, catalog_name, secrets and
 * external_access_integrations are empty, not NULL, and its is_secure is NULL, so a client matching the schema name of
 * every row - the Snowflake driver's getFunctions - reads each one (live-verified). A user function's row names its
 * schema and database.
 */
public class ShowBuiltinFunctionCellsTest extends MeasuredCellsTestSupport {

    @Test
    public void builtInRowsLeaveTheirSchemaAndCatalogEmpty() {
        engine.execute("CREATE FUNCTION prb_f1() RETURNS INT AS '1'");
        assertCells(new String[][] {
            {"SHOW FUNCTIONS ->> SELECT IFF(COUNT(*) > 0 AND COUNT_IF(\"schema_name\" = '') = COUNT(*) AND "
                + "COUNT_IF(\"catalog_name\" = '') = COUNT(*) AND COUNT_IF(\"secrets\" = '') = COUNT(*) AND "
                + "COUNT_IF(\"external_access_integrations\" = '') = COUNT(*) AND COUNT_IF(\"is_secure\" IS "
                + "NULL) = COUNT(*), 'Y', 'N') FROM $1 WHERE \"is_builtin\" = 'Y'",
                "Y"},
            {"SHOW FUNCTIONS LIKE 'ABS' ->> SELECT '[' || \"schema_name\" || '][' || \"catalog_name\" || "
                + "'][' || \"secrets\" || '][' || \"external_access_integrations\" || '][' || "
                + "NVL(\"is_secure\", 'NULL') || ']' FROM $1",
                "[][][][][NULL]"},
            {"SHOW FUNCTIONS LIKE 'SUM' ->> SELECT '[' || \"schema_name\" || '][' || \"catalog_name\" || "
                + "']' FROM $1",
                "[][]"},
            {"SHOW FUNCTIONS LIKE 'ABS' IN SCHEMA test_schema ->> SELECT '[' || \"schema_name\" || '][' || "
                + "\"catalog_name\" || ']' FROM $1",
                "[][]"},
            {"SHOW BUILTIN FUNCTIONS LIKE 'ABS' ->> SELECT '[' || \"schema_name\" || '][' || "
                + "\"catalog_name\" || ']' FROM $1",
                "[][]"},
            {"SHOW USER FUNCTIONS LIKE 'PRB_F1' ->> SELECT IFF(\"schema_name\" = CURRENT_SCHEMA(), 'Y', 'N') "
                + "|| '|' || IFF(\"catalog_name\" = CURRENT_DATABASE(), 'Y', 'N') || '|' || \"is_builtin\" "
                + "FROM $1",
                "Y|Y|N"},
        });
    }
}
