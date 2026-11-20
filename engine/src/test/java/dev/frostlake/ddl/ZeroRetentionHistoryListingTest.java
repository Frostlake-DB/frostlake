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

import java.util.Locale;

/**
 * A version purged with a retention of 0 days is still listed by SHOW ... HISTORY, its dropped_on set, while UNDROP no
 * longer finds it (live-verified): a table, a schema and a database, replaced or dropped, every version of the name
 * however often it is dropped again. A schema or a database replaced while it keeps its data is a dropped version
 * UNDROP restores once its name is free (live-verified).
 */
public class ZeroRetentionHistoryListingTest extends MeasuredCellsTestSupport {

    @Test
    public void zeroRetentionVersionsAreListedButNotUndropped() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE zr916 (a INT) DATA_RETENTION_TIME_IN_DAYS = 0",
                "Table ZR916 successfully created."},
            {"CREATE OR REPLACE TABLE zr916 (b INT)", "Table ZR916 successfully created."},
            {"SHOW TABLES HISTORY LIKE 'ZR916' ->> SELECT \"name\", \"dropped_on\" IS NOT NULL FROM $1 ORDER BY 2",
                "ZR916|false ;; ZR916|true"},
            {"CREATE OR REPLACE TABLE zd916 (a INT) DATA_RETENTION_TIME_IN_DAYS = 0",
                "Table ZD916 successfully created."},
            {"DROP TABLE zd916", "ZD916 successfully dropped."},
            {"SHOW TABLES HISTORY LIKE 'ZD916' ->> SELECT \"name\", \"dropped_on\" IS NOT NULL FROM $1 ORDER BY 2",
                "ZD916|true"},
            {"UNDROP TABLE zd916", "REFUSED Table ZD916 did not exist or was purged."},
            {"CREATE OR REPLACE SCHEMA zs916 DATA_RETENTION_TIME_IN_DAYS = 0", "Schema ZS916 successfully created."},
            {"DROP SCHEMA zs916", "ZS916 successfully dropped."},
            {"SHOW SCHEMAS HISTORY LIKE 'ZS916' ->> SELECT \"name\", \"dropped_on\" IS NOT NULL FROM $1", "ZS916|true"},
            {"UNDROP SCHEMA zs916", "REFUSED Schema ZS916 did not exist or was purged."},
            {"USE SCHEMA test_schema", "Statement executed successfully."},
        });
    }

    /** A history listing's versions of a name, each with whether it is dropped. */
    private static String listed(final String name, final String... dropped) {
        final StringBuilder out = new StringBuilder();
        for (final String flag : dropped) {
            out.append(out.length() == 0 ? "" : " ;; ").append(name).append('|').append(flag);
        }
        return out.toString();
    }

    @Test
    public void everyVersionOfANameIsListed() {
        final String tables = "SHOW TABLES HISTORY LIKE 'ZT66' ->> SELECT \"name\", \"dropped_on\" IS NOT NULL FROM $1 "
            + "ORDER BY 2";
        final String schemas = "SHOW SCHEMAS HISTORY LIKE 'ZS66' IN DATABASE test_db ->> SELECT \"name\", "
            + "\"dropped_on\" IS NOT NULL FROM $1 ORDER BY 2";
        final String replaced = "CREATE OR REPLACE TABLE zt66 (a INT) DATA_RETENTION_TIME_IN_DAYS = 0";
        assertCells(new String[][] {
            {replaced, "Table ZT66 successfully created."},
            {replaced, "Table ZT66 successfully created."},
            {replaced, "Table ZT66 successfully created."},
            {tables, listed("ZT66", "false", "true", "true")},
            {"DROP TABLE zt66", "ZT66 successfully dropped."},
            {tables, listed("ZT66", "true", "true", "true")},
            {"CREATE TABLE zt66 (a INT) DATA_RETENTION_TIME_IN_DAYS = 0", "Table ZT66 successfully created."},
            {"DROP TABLE zt66", "ZT66 successfully dropped."},
            {tables, listed("ZT66", "true", "true", "true", "true")},
            {"CREATE OR REPLACE SCHEMA zs66 DATA_RETENTION_TIME_IN_DAYS = 0", "Schema ZS66 successfully created."},
            {"CREATE OR REPLACE SCHEMA zs66 DATA_RETENTION_TIME_IN_DAYS = 0", "Schema ZS66 successfully created."},
            {schemas, listed("ZS66", "false", "true")},
            {"DROP SCHEMA zs66", "ZS66 successfully dropped."},
            {schemas, listed("ZS66", "true", "true")},
            {"USE SCHEMA test_db.test_schema", "Statement executed successfully."},
            {"CREATE OR REPLACE TABLE zk66 (a INT)", "Table ZK66 successfully created."},
            {"CREATE OR REPLACE TABLE zk66 (a INT) DATA_RETENTION_TIME_IN_DAYS = 0",
                "Table ZK66 successfully created."},
            {"DROP TABLE zk66", "ZK66 successfully dropped."},
            {"UNDROP TABLE zk66", "Table ZK66 successfully restored."},
            {"SHOW TABLES HISTORY LIKE 'ZK66' ->> SELECT \"dropped_on\" IS NOT NULL, \"retention_time\" FROM $1 "
                + "ORDER BY 1, 2", "false|1 ;; true|0"},
        });
    }

    @Test
    public void aDatabaseOfNoRetention() {
        // A name of this run's own: the account lists a purged database's versions past the test.
        final String name = "FL_ZD_" + Long.toString(System.nanoTime() % 2176782336L, 36).toUpperCase(Locale.ROOT);
        final String history = "SHOW DATABASES HISTORY LIKE '" + name + "' ->> SELECT \"name\", \"dropped_on\" IS NOT "
            + "NULL FROM $1 ORDER BY 2";
        try {
            assertCells(new String[][] {
                {"CREATE OR REPLACE DATABASE " + name + " DATA_RETENTION_TIME_IN_DAYS = 0",
                    "Database " + name + " successfully created."},
                {"CREATE OR REPLACE DATABASE " + name + " DATA_RETENTION_TIME_IN_DAYS = 0",
                    "Database " + name + " successfully created."},
                {history, listed(name, "false", "true")},
                {"DROP DATABASE " + name, name + " successfully dropped."},
                {history, listed(name, "true", "true")},
                {"UNDROP DATABASE " + name, "REFUSED Database " + name + " did not exist or was purged."},
            });
        } finally {
            engine.execute("DROP DATABASE IF EXISTS " + name);
            engine.execute("USE SCHEMA test_db.test_schema");
        }
    }

    @Test
    public void aReplacedSchemaOrDatabaseIsKept() {
        final String name = "FL_ZR_" + Long.toString(System.nanoTime() % 2176782336L, 36).toUpperCase(Locale.ROOT);
        final String renamed = name + "_E";
        try {
            assertCells(new String[][] {
                {"CREATE OR REPLACE SCHEMA zs68 DATA_RETENTION_TIME_IN_DAYS = 1 COMMENT = 'one'",
                    "Schema ZS68 successfully created."},
                {"CREATE OR REPLACE SCHEMA zs68 DATA_RETENTION_TIME_IN_DAYS = 1 COMMENT = 'two'",
                    "Schema ZS68 successfully created."},
                {"SHOW SCHEMAS HISTORY LIKE 'ZS68' IN DATABASE test_db ->> SELECT \"name\", \"dropped_on\" IS NOT "
                    + "NULL, \"comment\", \"retention_time\" FROM $1 ORDER BY 2", "ZS68|false|two|1 ;; ZS68|true|one|1"},
                {"UNDROP SCHEMA zs68", "REFUSED SQL compilation error: | Object 'ZS68' already exists."},
                {"ALTER SCHEMA zs68 RENAME TO zs68b", "Statement executed successfully."},
                {"UNDROP SCHEMA zs68", "Schema ZS68 successfully restored."},
                {"SHOW SCHEMAS LIKE 'ZS68%' IN DATABASE test_db ->> SELECT \"name\", \"comment\" FROM $1 ORDER BY 1",
                    "ZS68|one ;; ZS68B|two"},
                {"CREATE OR REPLACE DATABASE " + name + " COMMENT = 'one'",
                    "Database " + name + " successfully created."},
                {"CREATE OR REPLACE DATABASE " + name + " COMMENT = 'two'",
                    "Database " + name + " successfully created."},
                {"SHOW DATABASES HISTORY LIKE '" + name + "' ->> SELECT \"name\", \"dropped_on\" IS NOT NULL, "
                    + "\"comment\", \"retention_time\" FROM $1 ORDER BY 2",
                    name + "|false|two|1 ;; " + name + "|true|one|1"},
                {"UNDROP DATABASE " + name, "REFUSED SQL compilation error: | Object '" + name + "' already exists."},
                {"ALTER DATABASE " + name + " RENAME TO " + renamed, "Statement executed successfully."},
                {"UNDROP DATABASE " + name, "Database " + name + " successfully restored."},
                {"SHOW DATABASES LIKE '" + name + "%' ->> SELECT \"name\", \"comment\" FROM $1 ORDER BY 1",
                    name + "|one ;; " + renamed + "|two"},
            });
        } finally {
            // Purged at once: a database dropped at 0 days' retention takes its versions and schemas with it.
            for (final String database : new String[] {name, renamed}) {
                engine.execute("ALTER DATABASE IF EXISTS " + database + " SET DATA_RETENTION_TIME_IN_DAYS = 0");
                engine.execute("DROP DATABASE IF EXISTS " + database);
            }
            engine.execute("USE SCHEMA test_db.test_schema");
        }
    }
}
