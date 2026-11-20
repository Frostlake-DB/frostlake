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
 * A stream on an external table: INSERT_ONLY alone, listed as an External Table's, and the refusals of a table or a
 * view named as the other kind (live-verified).
 */
public class ExternalTableStreamTest extends MeasuredCellsTestSupport {

    @Test
    public void anExternalTableTakesAnInsertOnlyStream() {
        engine.execute("CREATE STAGE xstg URL = 's3://bucket/path/' CREDENTIALS = (AWS_KEY_ID = 'k' AWS_SECRET_KEY ="
            + " 's')");
        engine.execute("CREATE EXTERNAL TABLE et (c1 VARCHAR AS ((value:c1::varchar))) LOCATION = @xstg "
            + "REFRESH_ON_CREATE = false AUTO_REFRESH = false FILE_FORMAT = (TYPE = CSV)");
        engine.execute("CREATE TABLE pt (a INT)");
        engine.execute("CREATE STREAM str ON EXTERNAL TABLE et INSERT_ONLY = true COMMENT = 'one'");
        engine.execute("ALTER STREAM str SET COMMENT = 'two'");
        assertCells(new String[][] {
            {"SHOW STREAMS LIKE 'STR' ->> SELECT \"table_name\" || '|' || \"source_type\" || '|' || "
                + "\"base_tables\" || '|' || \"type\" || '|' || \"stale\" || '|' || \"mode\" || '|' || "
                + "\"comment\" || '|' || \"invalid_reason\" FROM $1",
                "TEST_DB.TEST_SCHEMA.ET|External Table|TEST_DB.TEST_SCHEMA.ET|DELTA|false|INSERT_ONLY|two|N/A"},
            {"DESCRIBE STREAM str ->> SELECT \"source_type\" || '|' || \"mode\" FROM $1",
                "External Table|INSERT_ONLY"},
            {"SELECT COUNT(*) FROM str",
                "0"},
            {"SELECT GET_DDL('STREAM', 'STR')",
                "create or replace stream STR on external table ET insert_only = true;"},
            {"CREATE STREAM str2 ON TABLE et",
                "REFUSED SQL compilation error: Object found is of type 'EXTERNAL_TABLE', not specified type "
                + "'TABLE'."},
            {"CREATE STREAM str3 ON EXTERNAL TABLE pt INSERT_ONLY = true",
                "REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type "
                + "'EXTERNAL_TABLE'."},
            {"CREATE STREAM str4 ON EXTERNAL TABLE et",
                "REFUSED Streams on External Tables or Iceberg tables with an external catalog must have "
                + "INSERT_ONLY set to true."},
            {"CREATE STREAM str5 ON EXTERNAL TABLE et INSERT_ONLY = false",
                "REFUSED Streams on External Tables or Iceberg tables with an external catalog must have "
                + "INSERT_ONLY set to true."},
            {"CREATE STREAM str6 ON EXTERNAL TABLE et APPEND_ONLY = true",
                "REFUSED Streams on External Tables or Iceberg tables with an external catalog must have "
                + "INSERT_ONLY set to true."},
            {"CREATE STREAM str8 ON TABLE pt INSERT_ONLY = true",
                "REFUSED Streams of type INSERT_ONLY can only be created on external tables or Iceberg tables"
                + " with an external catalog integration."},
            {"CREATE STREAM str9 ON VIEW et",
                "REFUSED SQL compilation error: Object found is of type 'EXTERNAL_TABLE', not specified type "
                + "'VIEW'."},
        });
    }
}
