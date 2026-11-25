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
 * NULL in a property list outside a table's (live-verified): a stage's FILE_FORMAT, ENCRYPTION, DIRECTORY and
 * CREDENTIALS, a view's, a sequence's and a dynamic table's lists judge it by the option it is given, a parenthesized
 * NULL and a list of constants holding NULL are judged too, a list for a comment is held as the parser's word for it,
 * and ALTER TABLE, ALTER STAGE, ALTER VIEW and ALTER DYNAMIC TABLE judge what their lists hold before the object is
 * looked up.
 */
public class NullPropertyListsTest extends MeasuredCellsTestSupport {

    @Test
    public void nullAcrossKinds() {
        engine.execute("CREATE TABLE t1 (a INT)");
        engine.execute("CREATE STAGE st1");
        engine.execute("CREATE VIEW v1 AS SELECT 1 AS a");
        engine.execute("CREATE SEQUENCE sq1");
        assertCells(new String[][] {
            {"ALTER TABLE nosuch SET STAGE_FILE_FORMAT = (X = NULL)",
                "REFUSED SQL compilation error: | invalid parameter 'X'"},
            {"ALTER TABLE nosuch SET CHANGE_TRACKING = (A = NULL)",
                "REFUSED SQL compilation error: | invalid value [TOK_PROPERTY_LIST] for parameter "
                + "'CHANGE_TRACKING'"},
            {"ALTER TABLE nosuch SET STAGE_FILE_FORMAT = (FIELD_DELIMITER = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'FIELD_DELIMITER'"},
            {"ALTER TABLE nosuch SET NOPE = (A = NULL)",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'TABLE'"},
            {"ALTER TABLE nosuch SET NOPE = (A = 1)",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'TABLE'"},
            {"ALTER TABLE nosuch SET STAGE_FILE_FORMAT = (X = 1)",
                "REFUSED SQL compilation error: | invalid parameter 'X'"},
            {"ALTER TABLE nosuch SET CHANGE_TRACKING = (A = 1)",
                "REFUSED SQL compilation error: | invalid value [TOK_PROPERTY_LIST] for parameter "
                + "'CHANGE_TRACKING'"},
            {"ALTER TABLE nosuch SET NOPE = 1",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'TABLE'"},
            {"ALTER TABLE t1 SET CHANGE_TRACKING = (NULL)",
                "REFUSED Invalid value specified for property 'CHANGE_TRACKING'"},
            {"CREATE TABLE t2 (a INT) CHANGE_TRACKING = (NULL)",
                "REFUSED Invalid value specified for property 'CHANGE_TRACKING'"},
            {"ALTER TABLE t1 SET CHANGE_TRACKING = (NULL, 1)",
                "REFUSED Invalid value specified for property 'CHANGE_TRACKING'"},
            {"ALTER TABLE t1 SET STAGE_FILE_FORMAT = (NULL = NULL)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '='."},
            {"ALTER TABLE t1 SET DATA_RETENTION_TIME_IN_DAYS = (NULL)",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter "
                + "'DATA_RETENTION_TIME_IN_DAYS'"},
            {"ALTER TABLE t1 SET COMMENT = (NULL)",
                "REFUSED SQL compilation error: | invalid value 'null' for property 'COMMENT'"},
            {"ALTER TABLE t1 SET NOPE = (NULL)",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'TABLE'"},
            {"ALTER TABLE nosuch SET CHANGE_TRACKING = (NULL)",
                "REFUSED Invalid value specified for property 'CHANGE_TRACKING'"},
            {"ALTER TABLE t1 SET STAGE_FILE_FORMAT = (TYPE = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'TYPE'"},
            {"CREATE STAGE st2 FILE_FORMAT = (FIELD_DELIMITER = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'FIELD_DELIMITER'"},
            {"CREATE STAGE st2 FILE_FORMAT = (X = NULL)",
                "REFUSED SQL compilation error: | invalid parameter 'X'"},
            {"CREATE STAGE st2 FILE_FORMAT = (TYPE = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'TYPE'"},
            {"CREATE STAGE st2 ENCRYPTION = (TYPE = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'TYPE'"},
            {"CREATE STAGE st2 ENCRYPTION = (X = NULL)",
                "REFUSED SQL compilation error: | invalid parameter 'X'"},
            {"CREATE STAGE st2 DIRECTORY = (ENABLE = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'ENABLE'"},
            {"CREATE STAGE st2 DIRECTORY = (X = NULL)",
                "REFUSED SQL compilation error: | invalid parameter 'X'"},
            {"CREATE STAGE st2 URL = 's3://bucket/p/' CREDENTIALS = (AWS_KEY_ID = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'AWS_KEY_ID'"},
            {"CREATE STAGE st2 URL = 's3://bucket/p/' CREDENTIALS = (X = NULL)",
                "REFUSED SQL compilation error: | invalid parameter 'X'"},
            {"ALTER STAGE st1 SET FILE_FORMAT = (FIELD_DELIMITER = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'FIELD_DELIMITER'"},
            {"ALTER STAGE st1 SET FILE_FORMAT = (X = NULL)",
                "REFUSED SQL compilation error: | invalid parameter 'X'"},
            {"ALTER STAGE st1 SET DIRECTORY = (ENABLE = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'ENABLE'"},
            {"ALTER STAGE st1 SET ENCRYPTION = (TYPE = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'TYPE'"},
            {"ALTER STAGE nosuch SET FILE_FORMAT = (X = NULL)",
                "REFUSED SQL compilation error: | invalid parameter 'X'"},
            {"CREATE STAGE st2 NOPE = (A = NULL)",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'STAGE'"},
            {"ALTER STAGE st1 SET NOPE = (A = NULL)",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'STAGE'"},
            {"ALTER STAGE st1 SET FILE_FORMAT = (NULL = NULL)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '='."},
            {"ALTER VIEW v1 SET CHANGE_TRACKING = (A = NULL)",
                "REFUSED SQL compilation error: | invalid value [TOK_PROPERTY_LIST] for parameter "
                + "'CHANGE_TRACKING'"},
            {"ALTER VIEW v1 SET NOPE = (A = NULL)",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'VIEW'"},
            {"CREATE VIEW v2 CHANGE_TRACKING = (A = NULL) AS SELECT 1 AS a",
                "REFUSED SQL compilation error: | invalid value [TOK_PROPERTY_LIST] for parameter "
                + "'CHANGE_TRACKING'"},
            {"CREATE VIEW v2 NOPE = (A = NULL) AS SELECT 1 AS a",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'TABLE'"},
            {"ALTER VIEW v1 SET CHANGE_TRACKING = (NULL)",
                "REFUSED Invalid value specified for property 'CHANGE_TRACKING'"},
            {"CREATE SEQUENCE sq2 NOPE = (A = NULL)",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'SEQUENCE'"},
            {"ALTER SEQUENCE sq1 SET NOPE = (A = NULL)",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'SEQUENCE'"},
            {"ALTER SEQUENCE nosuch SET NOPE = (A = NULL)",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'SEQUENCE'"},
            {"CREATE SEQUENCE sq2 NOPE = (NULL)",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'SEQUENCE'"},
            {"CREATE DYNAMIC TABLE dt1 TARGET_LAG = '1 minute' WAREHOUSE = nowh NOPE = (A = NULL) AS "
                + "SELECT a FROM t1",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'DYNAMIC_TABLE'"},
            {"CREATE DYNAMIC TABLE dt1 TARGET_LAG = '1 minute' WAREHOUSE = nowh CHANGE_TRACKING = (A = "
                + "NULL) AS SELECT a FROM t1",
                "REFUSED SQL compilation error: | invalid property 'CHANGE_TRACKING' for 'DYNAMIC_TABLE'"},
            {"CREATE DYNAMIC TABLE dt1 TARGET_LAG = '1 minute' WAREHOUSE = nowh STAGE_FILE_FORMAT = "
                + "(FIELD_DELIMITER = NULL) AS SELECT a FROM t1",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'FIELD_DELIMITER'"},
            {"ALTER DYNAMIC TABLE nosuch SET NOPE = (A = NULL)",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'DYNAMIC_TABLE'"},
            {"CREATE DYNAMIC TABLE dt1 TARGET_LAG = '1 minute' WAREHOUSE = nowh CHANGE_TRACKING = (NULL) "
                + "AS SELECT a FROM t1",
                "REFUSED SQL compilation error: | invalid property 'CHANGE_TRACKING' for 'DYNAMIC_TABLE'"},
        });
    }

    @Test
    public void listShapes() {
        engine.execute("CREATE TABLE t1 (a INT)");
        engine.execute("CREATE STAGE st1");
        engine.execute("CREATE VIEW v1 AS SELECT 1 AS a");
        engine.execute("CREATE SEQUENCE sq1");
        assertCells(new String[][] {
            {"ALTER TABLE t1 SET STAGE_FILE_FORMAT = (A = NULL B = NULL)",
                "REFUSED SQL compilation error: | invalid parameter 'A'"},
            {"ALTER TABLE t1 SET STAGE_FILE_FORMAT = (A = 1, NULL = NULL)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'NULL'."},
            {"ALTER TABLE t1 SET STAGE_FILE_FORMAT = (NULL NULL)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'NULL'."},
            {"ALTER TABLE t1 SET COMMENT = (1)",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'COMMENT'"},
            {"ALTER TABLE t1 SET COMMENT = (NULL, NULL)",
                "REFUSED SQL compilation error: | invalid value 'null' for property 'COMMENT'"},
            {"ALTER TABLE t1 SET MAX_DATA_EXTENSION_TIME_IN_DAYS = (NULL)",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter "
                + "'MAX_DATA_EXTENSION_TIME_IN_DAYS'"},
            {"CREATE TABLE t3 (a INT) DATA_RETENTION_TIME_IN_DAYS = (NULL)",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter "
                + "'DATA_RETENTION_TIME_IN_DAYS'"},
            {"CREATE TABLE t3 (a INT) STAGE_FILE_FORMAT = (NULL = NULL)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '='."},
            {"CREATE TABLE t3 (a INT) COMMENT = (NULL)",
                "REFUSED SQL compilation error: | invalid value 'null' for property 'COMMENT'"},
            {"ALTER TABLE t1 SET STAGE_FILE_FORMAT = (FIELD_DELIMITER = (NULL))",
                "REFUSED SQL compilation error: | invalid value 'NULL' for property 'FIELD_DELIMITER'"},
            {"ALTER TABLE t1 SET STAGE_FILE_FORMAT = (NULL_IF = (NULL))",
                "REFUSED SQL compilation error: | invalid value 'NULL' for property 'NULL_IF'"},
            {"ALTER TABLE t1 SET STAGE_FILE_FORMAT = (NULL_IF = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'NULL_IF'"},
            {"ALTER TABLE nosuch SET DATA_RETENTION_TIME_IN_DAYS = (A = 1)",
                "REFUSED SQL compilation error: | invalid value [TOK_PROPERTY_LIST] for parameter "
                + "'DATA_RETENTION_TIME_IN_DAYS'"},
            {"ALTER TABLE nosuch SET DATA_RETENTION_TIME_IN_DAYS = (1)",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter "
                + "'DATA_RETENTION_TIME_IN_DAYS'"},
            {"ALTER TABLE nosuch SET CHANGE_TRACKING = ()",
                "REFUSED Invalid value specified for property 'CHANGE_TRACKING'"},
            {"ALTER TABLE nosuch SET STAGE_COPY_OPTIONS = (ON_ERROR = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'ON_ERROR'"},
            {"ALTER TABLE nosuch SET STAGE_COPY_OPTIONS = (X = 1)",
                "REFUSED SQL compilation error: invalid copy option 'X' | "},
            {"ALTER TABLE nosuch SET DATA_RETENTION_TIME_IN_DAYS = 1.5",
                "REFUSED SQL compilation error: | invalid value [1.5] for parameter "
                + "'DATA_RETENTION_TIME_IN_DAYS'"},
            {"ALTER TABLE nosuch SET CHANGE_TRACKING = 'x'",
                "REFUSED SQL compilation error: | invalid value ['x'] for parameter 'CHANGE_TRACKING'"},
            {"ALTER TABLE nosuch SET CHANGE_TRACKING = (1)",
                "REFUSED Invalid value specified for property 'CHANGE_TRACKING'"},
            {"ALTER VIEW v1 SET COMMENT = (NULL)",
                "REFUSED SQL compilation error: | invalid value 'null' for property 'COMMENT'"},
            {"ALTER VIEW v1 SET COMMENT = (1)",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'COMMENT'"},
            {"ALTER STAGE st1 SET COMMENT = (NULL)",
                "REFUSED SQL compilation error: | invalid value 'null' for property 'COMMENT'"},
            {"ALTER VIEW v1 SET CHANGE_TRACKING = (A = 1)",
                "REFUSED SQL compilation error: | invalid value [TOK_PROPERTY_LIST] for parameter "
                + "'CHANGE_TRACKING'"},
            {"ALTER VIEW v1 SET NOPE = 1",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'VIEW'"},
            {"ALTER SEQUENCE sq1 SET COMMENT = (NULL)",
                "REFUSED SQL compilation error: | invalid value 'null' for property 'COMMENT'"},
            {"ALTER VIEW nosuch SET NOPE = (A = NULL)",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'VIEW'"},
            {"ALTER VIEW nosuch SET CHANGE_TRACKING = (A = NULL)",
                "REFUSED SQL compilation error: | invalid value [TOK_PROPERTY_LIST] for parameter "
                + "'CHANGE_TRACKING'"},
            {"ALTER STAGE nosuch SET DIRECTORY = (ENABLE = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'ENABLE'"},
        });
    }

    @Test
    public void alterTableJudgedBeforeLookup() {
        engine.execute("CREATE TABLE t1 (a INT)");
        assertCells(new String[][] {
            {"ALTER TABLE nosuch SET DATA_RETENTION_TIME_IN_DAYS = 'x'",
                "REFUSED SQL compilation error: | invalid value ['x'] for parameter "
                + "'DATA_RETENTION_TIME_IN_DAYS'"},
            {"ALTER TABLE nosuch SET CHANGE_TRACKING = 1",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'CHANGE_TRACKING'"},
            {"ALTER TABLE nosuch SET ENABLE_SCHEMA_EVOLUTION = 'x'",
                "REFUSED SQL compilation error: | invalid value ['x'] for parameter 'ENABLE_SCHEMA_EVOLUTION'"},
            {"ALTER TABLE nosuch SET DATA_RETENTION_TIME_IN_DAYS = TRUE",
                "REFUSED SQL compilation error: | invalid value [TRUE] for parameter "
                + "'DATA_RETENTION_TIME_IN_DAYS'"},
            {"ALTER TABLE nosuch SET MAX_DATA_EXTENSION_TIME_IN_DAYS = 1.5",
                "REFUSED SQL compilation error: | invalid value [1.5] for parameter "
                + "'MAX_DATA_EXTENSION_TIME_IN_DAYS'"},
            {"ALTER TABLE nosuch SET STAGE_FILE_FORMAT = (TYPE = JSON FIELD_DELIMITER = ',')",
                "REFUSED SQL compilation error: | Option FIELD_DELIMITER is not valid for file format type "
                + "JSON."},
            {"ALTER TABLE nosuch SET COMMENT = (1)",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'COMMENT'"},
            {"ALTER TABLE nosuch SET CHANGE_TRACKING = (A = NULL) NOPE = 1",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'TABLE'"},
            {"ALTER TABLE nosuch SET DATA_RETENTION_TIME_IN_DAYS = 1.5 CHANGE_TRACKING = (A = 1)",
                "REFUSED SQL compilation error: | invalid value [1.5] for parameter "
                + "'DATA_RETENTION_TIME_IN_DAYS'"},
            {"ALTER TABLE t1 SET COMMENT = 'x' STAGE_FILE_FORMAT = (X = NULL)",
                "REFUSED SQL compilation error: | invalid parameter 'X'"},
            {"SELECT GET_DDL('TABLE', 't1')",
                "create or replace TABLE T1 ( | 	A NUMBER(38,0) | );"},
            {"ALTER TABLE IF EXISTS nosuch SET STAGE_FILE_FORMAT = (X = NULL)",
                "REFUSED SQL compilation error: | invalid parameter 'X'"},
            {"ALTER TABLE nosuch.nosch.nosuch SET STAGE_FILE_FORMAT = (X = NULL)",
                "REFUSED SQL compilation error: | invalid parameter 'X'"},
        });
    }

    @Test
    public void stageLists() {
        engine.execute("CREATE STAGE st1");
        assertCells(new String[][] {
            {"CREATE STAGE st5 URL = 's3://bucket/p/' CREDENTIALS = (X = 'a')",
                "REFUSED SQL compilation error: | invalid parameter 'X'"},
            {"CREATE STAGE st5 URL = 's3://bucket/p/' CREDENTIALS = (AWS_KEY_ID = 1)",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'AWS_KEY_ID'"},
            {"CREATE STAGE st6 CREDENTIALS = (AWS_KEY_ID = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'AWS_KEY_ID'"},
            {"CREATE STAGE st6 CREDENTIALS = (X = NULL)",
                "REFUSED SQL compilation error: | invalid parameter 'X'"},
            {"CREATE STAGE st6 URL = 'azure://acct.blob.core.windows.net/c/' CREDENTIALS = "
                + "(AZURE_SAS_TOKEN = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'AZURE_SAS_TOKEN'"},
            {"CREATE STAGE st6 URL = 's3://bucket/p/' CREDENTIALS = (AWS_KEY_ID = 'k' AWS_SECRET_KEY = "
                + "NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'AWS_SECRET_KEY'"},
            {"CREATE STAGE st6 URL = 's3://bucket/p/' CREDENTIALS = (AWS_KEY_ID = 'a:b' AWS_SECRET_KEY = "
                + "NULL)",
                "REFUSED SQL compilation error: | invalid value 'a:b' for property 'AWS_KEY_ID'"},
            {"CREATE STAGE st6 URL = 's3://bucket/p/' CREDENTIALS = (AWS_TOKEN = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'AWS_TOKEN'"},
            {"ALTER STAGE nosuch SET DIRECTORY = (ENABLE = 'x')",
                "REFUSED SQL compilation error: | invalid value ['x'] for parameter 'ENABLE'"},
            {"ALTER STAGE nosuch SET ENCRYPTION = (TYPE = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'TYPE'"},
            {"ALTER STAGE nosuch SET CREDENTIALS = (AWS_KEY_ID = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'AWS_KEY_ID'"},
            {"ALTER STAGE st1 SET CREDENTIALS = (AWS_KEY_ID = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'AWS_KEY_ID'"},
            {"ALTER STAGE st1 SET COMMENT = (1)",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'COMMENT'"},
            {"CREATE STAGE st7 COMMENT = (NULL)",
                "REFUSED SQL compilation error: | invalid value 'null' for property 'COMMENT'"},
            {"CREATE STAGE st7 COMMENT = (1)",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'COMMENT'"},
            {"CREATE STAGE st9 FILE_FORMAT = (NULL = NULL)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '='."},
            {"CREATE STAGE st9 ENCRYPTION = (NULL)",
                "REFUSED SQL compilation error: | invalid value [Not a property list: TOK_CONSTANT_LIST] for "
                + "parameter '{1}'"},
            {"CREATE STAGE st9 DIRECTORY = (ENABLE = TRUE X = NULL)",
                "REFUSED SQL compilation error: | invalid parameter 'X'"},
            {"ALTER STAGE nosuch SET COMMENT = (1)",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'COMMENT'"},
        });
    }

    @Test
    public void dynamicTableAndCreateLists() {
        engine.execute("CREATE TABLE t1 (a INT)");
        assertCells(new String[][] {
            {"ALTER DYNAMIC TABLE nosuch SET TARGET_LAG = (A = 1)",
                "REFUSED SQL compilation error: | invalid value 'TOK_PROPERTY_LIST' for property 'TARGET_LAG'"},
            {"ALTER DYNAMIC TABLE nosuch SET DATA_RETENTION_TIME_IN_DAYS = (1)",
                "REFUSED SQL compilation error: | invalid value [TOK_CONSTANT_LIST] for parameter "
                + "'DATA_RETENTION_TIME_IN_DAYS'"},
            {"ALTER DYNAMIC TABLE nosuch SET NOPE = (1)",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'DYNAMIC_TABLE'"},
            {"ALTER DYNAMIC TABLE nosuch SET COMMENT = (NULL)",
                "REFUSED SQL compilation error: | invalid value 'null' for property 'COMMENT'"},
            {"CREATE DYNAMIC TABLE dt1 TARGET_LAG = '1 minute' WAREHOUSE = nowh STAGE_FILE_FORMAT = (X = "
                + "1) AS SELECT a FROM t1",
                "REFUSED SQL compilation error: | invalid parameter 'X'"},
            {"CREATE DYNAMIC TABLE dt1 TARGET_LAG = '1 minute' WAREHOUSE = nowh STAGE_COPY_OPTIONS = "
                + "(ON_ERROR = NULL) AS SELECT a FROM t1",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'ON_ERROR'"},
            {"CREATE STAGE st5 URL = 's3://bucket/p/' CREDENTIALS = (AWS_ROLE = NULL)",
                "REFUSED SQL compilation error: | invalid value [NULL] for parameter 'AWS_ROLE'"},
            {"CREATE VIEW v4 COMMENT = (1) AS SELECT 1 AS a",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'COMMENT'"},
            {"CREATE VIEW v4 COMMENT = (NULL) AS SELECT 1 AS a",
                "REFUSED SQL compilation error: | invalid value 'null' for property 'COMMENT'"},
            {"CREATE VIEW v4 CHANGE_TRACKING = (NULL) AS SELECT 1 AS a",
                "REFUSED Invalid value specified for property 'CHANGE_TRACKING'"},
            {"CREATE STAGE st7 ENCRYPTION = (TYPE = 'SNOWFLAKE_SSE' X = NULL)",
                "REFUSED SQL compilation error: | invalid parameter 'X'"},
        });
    }

    @Test
    public void parenthesizedNull() {
        engine.execute("CREATE TABLE t1 (a INT)");
        engine.execute("CREATE STAGE st1");
        assertCells(new String[][] {
            {"ALTER TABLE t1 SET STAGE_FILE_FORMAT = (FIELD_DELIMITER = (NULL))",
                "REFUSED SQL compilation error: | invalid value 'NULL' for property 'FIELD_DELIMITER'"},
            {"ALTER TABLE t1 SET STAGE_FILE_FORMAT = (NULL_IF = (NULL))",
                "REFUSED SQL compilation error: | invalid value 'NULL' for property 'NULL_IF'"},
            {"ALTER STAGE st1 SET FILE_FORMAT = (FIELD_DELIMITER = (NULL))",
                "REFUSED SQL compilation error: | invalid value 'NULL' for property 'FIELD_DELIMITER'"},
            {"CREATE STAGE st2 FILE_FORMAT = (NULL_IF = (NULL))",
                "REFUSED SQL compilation error: | invalid value 'NULL' for property 'NULL_IF'"},
            {"ALTER TABLE t1 SET STAGE_FILE_FORMAT = (X = (NULL))",
                "REFUSED SQL compilation error: | invalid parameter 'X'"},
            {"ALTER TABLE t1 SET STAGE_FILE_FORMAT = (TYPE = JSON NULL_IF = (NULL))",
                "REFUSED SQL compilation error: | invalid value 'NULL' for property 'NULL_IF'"},
            {"ALTER TABLE t1 SET STAGE_FILE_FORMAT = (field_delimiter = (NULL))",
                "REFUSED SQL compilation error: | invalid value 'NULL' for property 'FIELD_DELIMITER'"},
        });
    }

    @Test
    public void listCommentsHeld() {
        engine.execute("CREATE STAGE st1");
        engine.execute("CREATE VIEW v1 AS SELECT 1 AS a");
        engine.execute("CREATE SEQUENCE sq1");
        engine.execute("ALTER STAGE st1 SET COMMENT = (A = NULL)");
        engine.execute("ALTER VIEW v1 SET COMMENT = (A = NULL)");
        engine.execute("ALTER SEQUENCE sq1 SET COMMENT = (A = NULL)");
        engine.execute("CREATE STAGE st2 COMMENT = (A = NULL B = 1)");
        engine.execute("CREATE SEQUENCE sq2 COMMENT = (A = NULL)");
        engine.execute("CREATE VIEW v2 COMMENT = (A = NULL) AS SELECT 1 AS a");
        engine.execute("CREATE TABLE t1 (a INT)");
        engine.execute("ALTER TABLE t1 SET COMMENT = (A = NULL)");
        engine.execute("ALTER TABLE t1 SET DEFAULT_DDL_COLLATION = (NULL)");
        assertCells(new String[][] {
            {"SELECT comment FROM information_schema.stages WHERE stage_name = 'ST1'",
                "TOK_PROPERTY_LIST"},
            {"SELECT comment FROM information_schema.views WHERE table_name = 'V1'",
                "TOK_PROPERTY_LIST"},
            {"SELECT comment FROM information_schema.sequences WHERE sequence_name = 'SQ1'",
                "TOK_PROPERTY_LIST"},
            {"SELECT comment FROM information_schema.stages WHERE stage_name = 'ST2'",
                "TOK_PROPERTY_LIST"},
            {"SELECT comment FROM information_schema.sequences WHERE sequence_name = 'SQ2'",
                "TOK_PROPERTY_LIST"},
            {"SELECT comment FROM information_schema.views WHERE table_name = 'V2'",
                "TOK_PROPERTY_LIST"},
            {"SELECT comment FROM information_schema.tables WHERE table_name = 'T1'",
                "TOK_PROPERTY_LIST"},
            {"SELECT GET_DDL('TABLE', 't1')",
                "create or replace TABLE T1 ( | 	A NUMBER(38,0) | )COMMENT='TOK_PROPERTY_LIST' | ;"},
        });
    }
}
