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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DBT projects, never created here: a CREATE judges where the project's files are read from — a FROM is wanted, a
 * stage named, the stage there and holding a dbt project — and refuses the project then; SHOW lists none, and every
 * statement that names one finds nothing.
 */
public class DbtProjectTest extends BaseDatabaseTest {

    private static final String NO_PROJECT_FILE = """
        SQL compilation error:
        DBT job failed with exception: Runtime Error
          No dbt_project.yml found at expected path /tmp/dbt/dbt_project.yml
          Verify that each entry within packages.yml (and their transitive dependencies) contains a file named \
        dbt_project.yml
         \s
         See the query's error message for details.""";

    private static final List<String> LISTED = Arrays.asList("external_access_integrations", "name",
        "database_name", "schema_name", "created_on", "updated_on", "owner", "comment", "dbt_version",
        "default_version", "default_version_name", "default_version_alias", "default_version_location_uri",
        "default_version_source_location_uri", "dbt_snowflake_version", "default_target", "default_environment",
        "default_writeback", "auto_compile", "last_deployed_from");

    private String refusalOf(final String sql) {
        final RuntimeException refusal = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
        return refusal.getMessage();
    }

    private String status(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private List<String> columnsOf(final ResultSet rs) {
        final List<String> names = new ArrayList<String>();
        for (final ResultSetColumn column : rs.getColumns()) {
            names.add(column.getName());
        }
        return names;
    }

    @Test
    public void aCreateJudgesTheProjectsLocation() {
        assertEquals("DBT project creation requires 'FROM <location>' clause.", refusalOf("CREATE DBT PROJECT dp2"));
        assertEquals("SQL compilation error:\ninvalid URL prefix found in: 'nosuch_location'",
            refusalOf("CREATE DBT PROJECT dp FROM 'nosuch_location'"));
        assertEquals("The specified stage NOSUCH_STAGE does not exist or the current role does not have access. "
            + "Owner must have at least READ on the specified stage.",
            refusalOf("CREATE DBT PROJECT dp FROM '@nosuch_stage'"));
        engine.execute("CREATE STAGE dst");
        assertEquals(NO_PROJECT_FILE, refusalOf("CREATE DBT PROJECT dp FROM '@dst'"));
        assertEquals(NO_PROJECT_FILE, refusalOf("CREATE DBT PROJECT dp FROM '@dst/sub'"));
        if (!isLiveSnowflake()) {
            assertEquals(NO_PROJECT_FILE, refusalOf("CREATE OR REPLACE DBT PROJECT dp FROM '@dst'"));
            assertEquals(NO_PROJECT_FILE, refusalOf("CREATE DBT PROJECT IF NOT EXISTS dp FROM '@dst'"));
            // A stage that holds a dbt project would make one, which this engine does not.
            stageLocalFile("dst", "dbt_project.yml", "name: p\n");
            assertEquals("Unsupported feature 'DBT PROJECT'.", refusalOf("CREATE DBT PROJECT dp FROM '@dst'"));
        }
    }

    @Test
    public void noProjectIsThere() {
        final ResultSet listed = engine.executeQuery("SHOW DBT PROJECTS");
        assertEquals(0, listed.getRowCount());
        assertEquals(LISTED, columnsOf(listed));
        assertEquals(LISTED, columnsOf(engine.executeQuery("SHOW TERSE DBT PROJECTS")));
        assertEquals(0, engine.executeQuery("SHOW DBT PROJECTS IN SCHEMA test_db.test_schema").getRowCount());
        final String missing = hinted("SQL compilation error:\nDBT PROJECT 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist "
            + "or not authorized.");
        assertEquals(missing, refusalOf("EXECUTE DBT PROJECT nosuch"));
        assertEquals(missing, refusalOf("DROP DBT PROJECT nosuch"));
        assertEquals(missing, refusalOf("DESCRIBE DBT PROJECT nosuch"));
        assertEquals("Drop statement executed successfully (NOSUCH already dropped).",
            status("DROP DBT PROJECT IF EXISTS nosuch"));
    }
}
