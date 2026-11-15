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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A task, a stage and a file format are cloned on their own as a schema clone copies them (documented: CREATE
 * ... CLONE, "Other schema objects: CREATE [ OR REPLACE ] { ALERT | FILE FORMAT | SEQUENCE | STAGE | STREAM |
 * TASK } [ IF NOT EXISTS ] object_name CLONE source_object_name"): a task suspended with its definition, an external
 * stage with its definition, a file format with its options. The three statements were syntax errors. A temporary clone
 * of an external stage or of a file format is its session's own; an internal stage is no clone's source, nor a
 * temporary one a permanent clone's, and a transient clone is a syntax error (live-verified).
 */
public class SchemaObjectCloneTest extends MeasuredCellsTestSupport {

    @Test
    public void aFileFormatClonesWithItsOptions() {
        engine.execute("CREATE OR REPLACE FILE FORMAT socff TYPE = CSV FIELD_DELIMITER = '|'");
        assertAnswer("File format SOCFF2 successfully created.", "CREATE OR REPLACE FILE FORMAT socff2 CLONE socff");
        final String described = answer("DESCRIBE FILE FORMAT socff2");
        assertTrue(described.contains("FIELD_DELIMITER|String|||,"), described);
        assertTrue(answer("CREATE FILE FORMAT socff2 CLONE socff").contains("'SOCFF2' already exists."),
            answer("CREATE FILE FORMAT socff2 CLONE socff"));
        assertAnswer("SOCFF2 already exists, statement succeeded.",
            "CREATE FILE FORMAT IF NOT EXISTS socff2 CLONE socff");
    }

    @Test
    public void aStageClonesWithItsDefinition() {
        engine.execute("CREATE OR REPLACE STAGE socext URL = 's3://bucket/path/'");
        assertAnswer("Stage area SOCEXT2 successfully created.", "CREATE OR REPLACE STAGE socext2 CLONE socext");
        final String listed = answer("SHOW STAGES LIKE 'SOCEXT2'");
        assertTrue(listed.contains("|s3://bucket/path/|"), listed);
    }

    @Test
    public void noInternalOrTemporaryStageIsAPermanentClonesSource() {
        engine.execute("CREATE OR REPLACE STAGE socint");
        engine.execute("CREATE OR REPLACE TEMPORARY STAGE soctint");
        engine.execute("CREATE OR REPLACE TEMPORARY STAGE soctext URL = 's3://bucket/path/'");
        engine.execute("CREATE OR REPLACE STAGE soctaken");
        final String internal = "REFUSED Unsupported feature 'Cloning internal and temporary stages'.";
        final String temporary = "REFUSED SQL compilation error: Temp stage cannot be cloned to a permanent stage.";
        assertAnswer(internal, "CREATE STAGE socint2 CLONE socint");
        assertAnswer(internal, "CREATE OR REPLACE STAGE socint2 CLONE socint");
        assertAnswer(internal, "CREATE STAGE IF NOT EXISTS socint2 CLONE socint");
        assertAnswer(internal, "CREATE STAGE IF NOT EXISTS soctaken CLONE socint");
        assertAnswer(internal, "CREATE STAGE socint3 CLONE soctint");
        assertAnswer(temporary, "CREATE STAGE soctext2 CLONE soctext");
        assertAnswer(temporary, "CREATE STAGE IF NOT EXISTS soctaken CLONE soctext");
        assertAnswer(temporary, "CREATE OR REPLACE STAGE soctaken CLONE soctext");
        assertAnswer("REFUSED SQL compilation error: | Object 'SOCTAKEN' already exists.",
            "CREATE STAGE soctaken CLONE socint");
        assertAnswer("REFUSED SQL compilation error: | Object 'SOCTAKEN' already exists.",
            "CREATE STAGE soctaken CLONE soctext");
        assertAnswer("Stage area SOCTEXT3 successfully created.", "CREATE TEMPORARY STAGE soctext3 CLONE soctext");
    }

    @Test
    public void aTaskClonesSuspended() {
        Assumptions.assumeFalse(isLiveSnowflake(), "makes a task the account would schedule");
        engine.execute("CREATE OR REPLACE TABLE soct (id INT)");
        engine.execute("CREATE OR REPLACE TASK soctk SCHEDULE = '60 MINUTE' AS INSERT INTO soct VALUES (1)");
        engine.execute("ALTER TASK soctk RESUME");
        assertAnswer("Task SOCTK2 successfully created.", "CREATE OR REPLACE TASK soctk2 CLONE soctk");
        final String shown = answer("SHOW TASKS LIKE 'SOCTK2'");
        assertTrue(shown.contains("|suspended|INSERT INTO soct VALUES (1)|"), shown);
        assertTrue(shown.contains("|60 MINUTE|"), shown);
        engine.execute("ALTER TASK soctk SUSPEND");
    }

    @Test
    public void aTemporaryCloneOfAnExternalStageOrAFormatIsItsSessions() {
        engine.execute("CREATE OR REPLACE STAGE socext URL = 's3://bucket/path/'");
        engine.execute("CREATE OR REPLACE STAGE socint");
        engine.execute("CREATE OR REPLACE FILE FORMAT socff TYPE = CSV FIELD_DELIMITER = '|'");
        assertAnswer("Stage area SOCTMP successfully created.", "CREATE TEMPORARY STAGE soctmp CLONE socext");
        assertAnswer("SOCTMP|s3://bucket/path/|EXTERNAL TEMPORARY",
            "SHOW STAGES LIKE 'SOCTMP' ->> SELECT \"name\", \"url\", \"type\" FROM $1");
        assertAnswer("REFUSED Unsupported feature 'Cloning internal and temporary stages'.",
            "CREATE TEMPORARY STAGE soctmp2 CLONE socint");
        assertAnswer("Stage area SOCTMP successfully created.",
            "CREATE OR REPLACE TEMPORARY STAGE soctmp CLONE socext");
        assertAnswer("SOCTMP already exists, statement succeeded.",
            "CREATE TEMPORARY STAGE IF NOT EXISTS soctmp CLONE socext");
        assertAnswer("File format SOCFFT successfully created.", "CREATE TEMPORARY FILE FORMAT socfft CLONE socff");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'STAGE'.",
            "CREATE TRANSIENT STAGE soctr CLONE socext");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'TASK'.",
            "CREATE TEMPORARY TASK soct CLONE nosuch_task");
    }
}
