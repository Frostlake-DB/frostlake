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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * External tables are kept as they are declared: CREATE EXTERNAL TABLE makes a table of VALUE and the columns
 * computed from it over an external stage, SHOW EXTERNAL TABLES lists it with its stage, location and file format,
 * DESCRIBE EXTERNAL TABLE answers its columns, a query over it answers no rows - no file is read - and DROP EXTERNAL
 * TABLE removes it. The refusals of a statement with no location, no file format, an unknown property or format
 * type, a missing stage and an internal stage are live-verified; the rest is embedded only and not measured
 * against an account, which needs an external stage for it.
 */
public class ExternalTableStatementsTest extends MeasuredCellsTestSupport {

    private static final String ACCOUNT_OBJECTS =
        "creates external tables over an external stage, which the account this suite is checked"
        + " against cannot reach";

    @Override
    protected void setupTest() {
        Assumptions.assumeFalse(isLiveSnowflake(), ACCOUNT_OBJECTS);
        engine.execute("CREATE OR REPLACE STAGE et_xs URL = 's3://bucket/path/'");
        engine.execute("CREATE OR REPLACE STAGE et_is");
        engine.execute("CREATE OR REPLACE TABLE et_plain (a INT)");
    }

    /** A statement's rows with each moment masked. */
    private String listed(final String sql) {
        return answer(sql).replaceAll("\\d{4}-\\d{2}-\\d{2}T[0-9:.]+Z", "<TS>");
    }

    @Test
    public void anExternalTableIsCreatedListedDescribedReadAndDropped() {
        assertAnswer("Table ET_A successfully created.",
            "CREATE EXTERNAL TABLE et_a (c1 VARCHAR AS (value:c1::varchar),"
            + " d DATE AS TO_DATE(metadata$filename)) PARTITION BY (d) WITH LOCATION = @et_xs/sub/"
            + " REFRESH_ON_CREATE = false AUTO_REFRESH = false PATTERN = '.*[.]csv' FILE_FORMAT = (TYPE = CSV)"
            + " COMMENT = 'one'");
        assertEquals("<TS>|ET_A|TEST_DB|TEST_SCHEMA|false|null|SYSADMIN|one|@TEST_DB.TEST_SCHEMA.ET_XS|s3://bucket"
            + "/path/sub/||CSV|AWS|null|null|null|UNSPECIFIED|null|ROLE",
            listed("SHOW EXTERNAL TABLES"));
        assertEquals("VALUE|VARIANT|COLUMN|Y|null|N|N|null|null|The value of this row|null|null|null ;; C1|VARCHAR"
            + "(16777216)|VIRTUAL|Y|null|N|N|null|CAST(GET(VALUE, 'c1') AS VARCHAR)|null|null|null|null ;; D|DATE"
            + "|VIRTUAL|Y|null|N|N|null|TO_DATE(METADATA$FILENAME)|null|null|null|null",
            answer("DESCRIBE EXTERNAL TABLE et_a"));
        assertEquals("", answer("SELECT * FROM et_a"));
        assertEquals("0", answer("SELECT COUNT(c1) FROM et_a"));
        assertAnswer("Statement executed successfully.", "ALTER EXTERNAL TABLE et_a REFRESH");
        assertAnswer("ET_A already exists, statement succeeded.", "CREATE EXTERNAL TABLE IF NOT EXISTS et_a"
            + " LOCATION = @et_xs FILE_FORMAT = (TYPE = JSON)");
        assertAnswer("REFUSED SQL compilation error: | Object 'ET_A' already exists.",
            "CREATE EXTERNAL TABLE et_a LOCATION = @et_xs FILE_FORMAT = (TYPE = JSON)");
        assertAnswer("Table ET_A successfully created.", "CREATE OR REPLACE EXTERNAL TABLE et_a LOCATION = @et_xs"
            + " FILE_FORMAT = (TYPE = JSON) COMMENT = 'two'");
        assertEquals("<TS>|ET_A|TEST_DB|TEST_SCHEMA|false|null|SYSADMIN|two|@TEST_DB.TEST_SCHEMA.ET_XS|s3://bucket"
            + "/path/||JSON|AWS|null|null|null|UNSPECIFIED|null|ROLE",
            listed("SHOW EXTERNAL TABLES LIKE 'ET_A'"));
        assertAnswer("ET_A successfully dropped.",
            "DROP EXTERNAL TABLE et_a");
        assertEquals("", listed("SHOW EXTERNAL TABLES"));
        assertAnswer("REFUSED SQL compilation error: | External table 'TEST_DB.TEST_SCHEMA.ET_A' does not exis"
            + "t or not authorized. Your primary role <ROLE> must have at least one privilege granted o"
            + "n TABLE TEST_DB.TEST_SCHEMA.ET_A.",
            "DROP EXTERNAL TABLE et_a");
        assertAnswer("Drop statement executed successfully (ET_A already dropped).",
            "DROP EXTERNAL TABLE IF EXISTS et_a");
    }

    @Test
    public void whatTheStatementLacksIsRefusedInOrder() {
        assertAnswer("REFUSED SQL compilation error: | Missing option(s): location", "CREATE EXTERNAL TABLE et_r");
        assertAnswer("REFUSED SQL compilation error: | Missing option(s): location",
            "CREATE EXTERNAL TABLE et_r FILE_FORMAT = (TYPE = CSV)");
        assertAnswer("REFUSED SQL compilation error: | Missing option(s): file_format",
            "CREATE EXTERNAL TABLE et_r LOCATION = @et_nostage");
        assertAnswer("REFUSED SQL compilation error: | Missing option(s): file_format",
            "CREATE EXTERNAL TABLE et_r LOCATION = @et_is");
        assertAnswer("REFUSED SQL compilation error: | invalid value [FOO] for parameter 'TYPE'",
            "CREATE EXTERNAL TABLE et_r LOCATION = @et_is FILE_FORMAT = (TYPE = FOO)");
        assertAnswer("REFUSED SQL compilation error: | invalid property 'FOO' for 'TABLE'",
            "CREATE EXTERNAL TABLE et_r LOCATION = @et_is FILE_FORMAT = (TYPE = CSV) FOO = 1");
        assertAnswer("REFUSED SQL compilation error: | Stage 'TEST_DB.TEST_SCHEMA.ET_NOSTAGE' does not exist or not"
            + " authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE"
            + " TEST_DB.TEST_SCHEMA.ET_NOSTAGE.",
            "CREATE EXTERNAL TABLE et_r LOCATION = @et_nostage FILE_FORMAT = (TYPE = CSV)");
        assertAnswer("REFUSED Cannot use internal stage ET_IS as the location for an external table.",
            "CREATE EXTERNAL TABLE et_r LOCATION = @et_is FILE_FORMAT = (TYPE = CSV)");
        assertAnswer("REFUSED Cannot use internal stage ET_IS as the location for an external table.",
            "CREATE EXTERNAL TABLE et_r (c1 VARCHAR) LOCATION = @et_is FILE_FORMAT = (TYPE = CSV)");
        assertAnswer("REFUSED Cannot use internal stage ET_IS as the location for an external table.",
            "CREATE EXTERNAL TABLE et_plain LOCATION = @et_is FILE_FORMAT = (TYPE = CSV)");
        assertAnswer("REFUSED SQL compilation error: | options IF NOT EXISTS and OR REPLACE are incompatible.",
            "CREATE OR REPLACE EXTERNAL TABLE IF NOT EXISTS et_r LOCATION = @et_is FILE_FORMAT = (TYPE = CSV)");
        assertAnswer("REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type"
            + " 'EXTERNAL_TABLE'.", "DROP EXTERNAL TABLE et_plain");
        assertAnswer("REFUSED SQL compilation error: | Invalid object type: 'TABLE'",
            "ALTER EXTERNAL TABLE et_plain REFRESH");
    }
}
