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

package dev.frostlake.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The SNOWFLAKE database's ACCOUNT_USAGE views besides QUERY_HISTORY: each with the account's columns and no rows
 * here, a query's columns typed as the account types them, and the share's refusals of what would write one or
 * recreate it (all live-verified).
 */
public class AccountUsageViewsTest extends MeasuredCellsTestSupport {

    /** Acts as ACCOUNTADMIN, the role these answers were measured under (see {@link #actAsAccountAdministrator}). */
    @BeforeEach
    public void actAsTheMeasuringRole() {
        actAsAccountAdministrator();
    }

    /** What DESCRIBE VIEW answers for a few of the views: each column's declared type and nullability. */
    @Test
    public void describedColumns() {
        assertAnswer("ROLE_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "CREATED_ON|TIMESTAMP_LTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "DELETED_ON|TIMESTAMP_LTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "NAME|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "COMMENT|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "OWNER|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "ROLE_TYPE|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "ROLE_DATABASE_NAME|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "ROLE_INSTANCE_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "OWNER_ROLE_TYPE|VARCHAR(13)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "IS_FROM_ORGANIZATION_USER_GROUP|BOOLEAN|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW SNOWFLAKE.ACCOUNT_USAGE.ROLES");
        assertAnswer("SEMANTIC_TABLE_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SEMANTIC_TABLE_NAME|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SEMANTIC_VIEW_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SEMANTIC_VIEW_NAME|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SEMANTIC_VIEW_SCHEMA_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SEMANTIC_VIEW_SCHEMA_NAME|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SEMANTIC_VIEW_DATABASE_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SEMANTIC_VIEW_DATABASE_NAME|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;;"
                + " BASE_TABLE_NAME|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "BASE_TABLE_SCHEMA_NAME|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "BASE_TABLE_DATABASE_NAME|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "PRIMARY_KEYS|ARRAY(VARCHAR(16777216))|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "UNIQUE_KEYS|ARRAY(ARRAY(VARCHAR(16777216)))|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SYNONYMS|ARRAY(VARCHAR(16777216))|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "DISTINCT_RANGES|ARRAY(OBJECT)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "CREATED|TIMESTAMP_LTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "LAST_ALTERED|TIMESTAMP_LTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "DELETED|TIMESTAMP_LTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "COMMENT|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "DEFINITION|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW SNOWFLAKE.ACCOUNT_USAGE.SEMANTIC_TABLES");
        assertAnswer("USAGE_DATE|DATE|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "DATABASE_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "DATABASE_NAME|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "DELETED|TIMESTAMP_LTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "AVERAGE_DATABASE_BYTES|FLOAT|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "AVERAGE_FAILSAFE_BYTES|FLOAT|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "AVERAGE_HYBRID_TABLE_STORAGE_BYTES|FLOAT|COLUMN|N|null|N|N|null|null|null|null|null|null ;; "
                + "AVERAGE_ARCHIVE_STORAGE_COOL_BYTES|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null "
                + ";; "
                + "AVERAGE_ARCHIVE_STORAGE_COLD_BYTES|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null "
                + ";; "
                + "AVERAGE_ARCHIVE_STORAGE_COOL_FAILSAFE_BYTES|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null"
                + " ;; "
                + "AVERAGE_ARCHIVE_STORAGE_COLD_FAILSAFE_BYTES|NUMBER(38,0)|COLUMN|N|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW SNOWFLAKE.ACCOUNT_USAGE.DATABASE_STORAGE_USAGE_HISTORY");
    }

    /**
     * The columns a query of a few of the views answers: a text declared at the default width mostly at the full
     * text width, a few at the declared width or wider, a structured array's text elements at the full width too.
     */
    @Test
    public void queriedColumns() {
        engine.execute("SELECT * FROM SNOWFLAKE.ACCOUNT_USAGE.TASK_HISTORY WHERE 1 = 0");
        assertAnswer("NAME|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "QUERY_TEXT|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "CONDITION_TEXT|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SCHEMA_NAME|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "TASK_SCHEMA_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "DATABASE_NAME|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "TASK_DATABASE_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SCHEDULED_TIME|TIMESTAMP_LTZ(3)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "COMPLETED_TIME|TIMESTAMP_LTZ(3)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "STATE|VARCHAR(25)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "RETURN_VALUE|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "QUERY_ID|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "QUERY_START_TIME|TIMESTAMP_LTZ(3)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "ERROR_CODE|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "ERROR_MESSAGE|VARCHAR(16777232)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "GRAPH_VERSION|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "RUN_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "ROOT_TASK_ID|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SCHEDULED_FROM|VARCHAR(15)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "INSTANCE_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "ATTEMPT_NUMBER|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "CONFIG|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "QUERY_HASH|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "QUERY_HASH_VERSION|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "QUERY_PARAMETERIZED_HASH|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "QUERY_PARAMETERIZED_HASH_VERSION|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;;"
                + " GRAPH_RUN_GROUP_ID|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "BACKFILL_INFO|OBJECT|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SPCS_JOB_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SCHEDULED_BY_USER|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "GRAPH_ID|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE RESULT LAST_QUERY_ID()");
        engine.execute("SELECT * FROM SNOWFLAKE.ACCOUNT_USAGE.SEMANTIC_DIMENSIONS WHERE 1 = 0");
        assertAnswer("SEMANTIC_DIMENSION_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SEMANTIC_DIMENSION_NAME|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SEMANTIC_TABLE_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SEMANTIC_TABLE_NAME|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SEMANTIC_VIEW_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SEMANTIC_VIEW_NAME|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SEMANTIC_VIEW_SCHEMA_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SEMANTIC_VIEW_SCHEMA_NAME|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SEMANTIC_VIEW_DATABASE_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SEMANTIC_VIEW_DATABASE_NAME|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null "
                + ";; DATA_TYPE|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "EXPRESSION|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "SYNONYMS|ARRAY(VARCHAR(134217728))|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "CREATED|TIMESTAMP_LTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "LAST_ALTERED|TIMESTAMP_LTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "DELETED|TIMESTAMP_LTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "COMMENT|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "CORTEX_SEARCH_SERVICE_DATABASE|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null"
                + " ;; "
                + "CORTEX_SEARCH_SERVICE_SCHEMA|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null "
                + ";; CORTEX_SEARCH_SERVICE|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "CORTEX_SEARCH_SERVICE_COLUMN_NAME|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null"
                + " ;; LABELS|ARRAY(VARCHAR(134217728))|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "LOD_DIMENSIONS|ARRAY(OBJECT)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "LOD_DIMENSION_TYPE|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "ACCESS_MODIFIER|VARCHAR(7)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE RESULT LAST_QUERY_ID()");
        engine.execute("SELECT * FROM SNOWFLAKE.ACCOUNT_USAGE.BILLING_DOCUMENTS WHERE 1 = 0");
        assertAnswer("ORGANIZATION_NAME|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "CUSTOMER_NAME|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "CONTRACT_NUMBER|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "STATEMENT_DATE|DATE|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "DOCUMENT_BYTES|BINARY(8388608)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "DOCUMENT_FORMAT|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "DOCUMENT_TYPE|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "DOCUMENT_NAME|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "CHANNEL_TYPE|VARCHAR(8)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE RESULT LAST_QUERY_ID()");
    }

    /** A read of a view finds no rows here; on the account it finds none under a filter nothing passes. */
    @Test
    public void readsUnderAFilterNothingPasses() {
        assertAnswer("0", "SELECT COUNT(*) FROM SNOWFLAKE.ACCOUNT_USAGE.LOGIN_HISTORY WHERE 1 = 0");
        assertAnswer("0", "SELECT COUNT(*) FROM SNOWFLAKE.ACCOUNT_USAGE.TASK_HISTORY WHERE NAME = 'no_such_task_x'");
    }

    /** A view is listed as QUERY_HISTORY is: secure, owned by no role, its row past its creation time alike. */
    @Test
    public void listedAsTheShareLists() {
        final String view = answer("SHOW VIEWS LIKE 'DATABASES' IN SCHEMA SNOWFLAKE.ACCOUNT_USAGE");
        assertEquals("DATABASES||SNOWFLAKE|ACCOUNT_USAGE||||true|false||OFF", view.substring(view.indexOf('|') + 1));
        final String terse = answer("SHOW TERSE VIEWS LIKE 'ROLES' IN SCHEMA SNOWFLAKE.ACCOUNT_USAGE");
        assertEquals("ROLES|VIEW|SNOWFLAKE|ACCOUNT_USAGE", terse.substring(terse.indexOf('|') + 1));
    }

    /** Writing a view is the share provider's, and GET_DDL recreates nothing of the shared database. */
    @Test
    public void theShareRefusesWritesAndDefinitions() {
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on view 'QUERY_HISTORY'. "
                + "Provider share does not have sufficient privileges.",
            "INSERT INTO SNOWFLAKE.ACCOUNT_USAGE.QUERY_HISTORY (QUERY_ID) VALUES ('x')");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on view 'ROLES'. Provider "
                + "share does not have sufficient privileges.",
            "DELETE FROM SNOWFLAKE.ACCOUNT_USAGE.ROLES");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on view 'ROLES'. Provider "
                + "share does not have sufficient privileges.",
            "UPDATE SNOWFLAKE.ACCOUNT_USAGE.ROLES SET NAME = 'x'");
        final String shared = "REFUSED SQL compilation error: This operation is not supported on shared database "
            + "'SNOWFLAKE'.";
        assertAnswer(shared, "SELECT GET_DDL('VIEW', 'SNOWFLAKE.ACCOUNT_USAGE.ROLES')");
        assertAnswer(shared, "SELECT GET_DDL('SCHEMA', 'SNOWFLAKE.ACCOUNT_USAGE')");
        assertAnswer(shared, "SELECT GET_DDL('DATABASE', 'SNOWFLAKE')");
    }

    /**
     * SHOW COLUMNS describes a column as the account does: some texts at one byte per character, some
     * VARIANT, OBJECT and ARRAY columns with {@code "fixed":false}.
     */
    @Test
    public void describedAsTheAccountDescribesThem() {
        engine.execute("SHOW COLUMNS IN VIEW SNOWFLAKE.ACCOUNT_USAGE.AGGREGATE_ACCESS_HISTORY");
        assertAnswer("INTERVAL_START_TIME|{\"type\":\"TIMESTAMP_LTZ\",\"precision\":0,\"scale\":9,"
                + "\"nullable\":true} ;; INTERVAL_END_TIME|{\"type\":\"TIMESTAMP_LTZ\",\"precision\":0,"
                + "\"scale\":9,\"nullable\":true} ;; QUERY_PARAMETERIZED_HASH|{\"type\":\"TEXT\","
                + "\"length\":16777216,\"byteLength\":16777216,\"nullable\":true,\"fixed\":false} ;; "
                + "USER_NAME|{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":16777216,"
                + "\"nullable\":true,\"fixed\":false} ;; CALLS|{\"type\":\"FIXED\",\"precision\":38,"
                + "\"scale\":0,\"nullable\":true} ;; DIRECT_OBJECTS_ACCESSED|{\"type\":\"ARRAY\","
                + "\"nullable\":true} ;; BASE_OBJECTS_ACCESSED|{\"type\":\"ARRAY\",\"nullable\":true} ;; "
                + "OBJECTS_MODIFIED|{\"type\":\"ARRAY\",\"nullable\":true} ;; "
                + "OBJECT_MODIFIED_BY_DDL|{\"type\":\"OBJECT\",\"nullable\":true} ;; "
                + "POLICIES_REFERENCED|{\"type\":\"ARRAY\",\"nullable\":true}",
            "SELECT \"column_name\", \"data_type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        engine.execute("SHOW COLUMNS IN VIEW SNOWFLAKE.ACCOUNT_USAGE.CORTEX_CODE_CLI_USAGE_HISTORY");
        assertAnswer("USER_ID|{\"type\":\"FIXED\",\"precision\":38,\"scale\":0,\"nullable\":true} ;; "
                + "USER_NAME|{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":67108864,"
                + "\"nullable\":true,\"fixed\":false} ;; USER_TAGS|{\"type\":\"ARRAY\",\"nullable\":true} "
                + ";; REQUEST_ID|{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":67108864,"
                + "\"nullable\":true,\"fixed\":false} ;; PARENT_REQUEST_ID|{\"type\":\"TEXT\","
                + "\"length\":16777216,\"byteLength\":67108864,\"nullable\":true,\"fixed\":false} ;; "
                + "USAGE_TIME|{\"type\":\"TIMESTAMP_TZ\",\"precision\":0,\"scale\":9,\"nullable\":true} ;; "
                + "TOKEN_CREDITS|{\"type\":\"FIXED\",\"precision\":38,\"scale\":9,\"nullable\":true} ;; "
                + "TOKENS|{\"type\":\"FIXED\",\"precision\":38,\"scale\":0,\"nullable\":true} ;; "
                + "TOKENS_GRANULAR|{\"type\":\"OBJECT\",\"nullable\":false,\"fixed\":false} ;; "
                + "CREDITS_GRANULAR|{\"type\":\"OBJECT\",\"nullable\":false,\"fixed\":false} ;; "
                + "METADATA|{\"type\":\"OBJECT\",\"nullable\":true} ;; THREAD_ID|{\"type\":\"FIXED\","
                + "\"precision\":38,\"scale\":0,\"nullable\":true} ;; "
                + "PREMIUM_TOKENS_GRANULAR|{\"type\":\"OBJECT\",\"nullable\":true,\"fixed\":false} ;; "
                + "PREMIUM_CREDITS_GRANULAR|{\"type\":\"OBJECT\",\"nullable\":true,\"fixed\":false}",
            "SELECT \"column_name\", \"data_type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        engine.execute("SHOW COLUMNS IN VIEW SNOWFLAKE.ACCOUNT_USAGE.SEMANTIC_DIMENSIONS");
        assertAnswer("SEMANTIC_DIMENSION_ID|{\"type\":\"FIXED\",\"precision\":38,\"scale\":0,"
                + "\"nullable\":true} ;; SEMANTIC_DIMENSION_NAME|{\"type\":\"TEXT\",\"length\":16777216,"
                + "\"byteLength\":67108864,\"nullable\":true,\"fixed\":false} ;; "
                + "SEMANTIC_TABLE_ID|{\"type\":\"FIXED\",\"precision\":38,\"scale\":0,\"nullable\":true} ;;"
                + " SEMANTIC_TABLE_NAME|{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":67108864,"
                + "\"nullable\":true,\"fixed\":false} ;; SEMANTIC_VIEW_ID|{\"type\":\"FIXED\","
                + "\"precision\":38,\"scale\":0,\"nullable\":true} ;; "
                + "SEMANTIC_VIEW_NAME|{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":67108864,"
                + "\"nullable\":true,\"fixed\":false} ;; SEMANTIC_VIEW_SCHEMA_ID|{\"type\":\"FIXED\","
                + "\"precision\":38,\"scale\":0,\"nullable\":true} ;; "
                + "SEMANTIC_VIEW_SCHEMA_NAME|{\"type\":\"TEXT\",\"length\":16777216,"
                + "\"byteLength\":67108864,\"nullable\":true,\"fixed\":false} ;; "
                + "SEMANTIC_VIEW_DATABASE_ID|{\"type\":\"FIXED\",\"precision\":38,\"scale\":0,"
                + "\"nullable\":true} ;; SEMANTIC_VIEW_DATABASE_NAME|{\"type\":\"TEXT\","
                + "\"length\":16777216,\"byteLength\":67108864,\"nullable\":true,\"fixed\":false} ;; "
                + "DATA_TYPE|{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":16777216,"
                + "\"nullable\":true,\"fixed\":false} ;; EXPRESSION|{\"type\":\"TEXT\",\"length\":16777216,"
                + "\"byteLength\":67108864,\"nullable\":true,\"fixed\":false} ;; "
                + "SYNONYMS|{\"type\":\"ARRAY\",\"nullable\":true,\"elementType\":{\"type\":\"TEXT\","
                + "\"length\":16777216,\"byteLength\":67108864,\"nullable\":true,\"fixed\":false}} ;; "
                + "CREATED|{\"type\":\"TIMESTAMP_LTZ\",\"precision\":0,\"scale\":6,\"nullable\":true} ;; "
                + "LAST_ALTERED|{\"type\":\"TIMESTAMP_LTZ\",\"precision\":0,\"scale\":6,\"nullable\":true} "
                + ";; DELETED|{\"type\":\"TIMESTAMP_LTZ\",\"precision\":0,\"scale\":6,\"nullable\":true} ;;"
                + " COMMENT|{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":67108864,"
                + "\"nullable\":true,\"fixed\":false} ;; CORTEX_SEARCH_SERVICE_DATABASE|{\"type\":\"TEXT\","
                + "\"length\":16777216,\"byteLength\":67108864,\"nullable\":true,\"fixed\":false} ;; "
                + "CORTEX_SEARCH_SERVICE_SCHEMA|{\"type\":\"TEXT\",\"length\":16777216,"
                + "\"byteLength\":67108864,\"nullable\":true,\"fixed\":false} ;; "
                + "CORTEX_SEARCH_SERVICE|{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":67108864,"
                + "\"nullable\":true,\"fixed\":false} ;; "
                + "CORTEX_SEARCH_SERVICE_COLUMN_NAME|{\"type\":\"TEXT\",\"length\":16777216,"
                + "\"byteLength\":67108864,\"nullable\":true,\"fixed\":false} ;; "
                + "LABELS|{\"type\":\"ARRAY\",\"nullable\":true,\"elementType\":{\"type\":\"TEXT\","
                + "\"length\":16777216,\"byteLength\":67108864,\"nullable\":true,\"fixed\":false}} ;; "
                + "LOD_DIMENSIONS|{\"type\":\"ARRAY\",\"nullable\":true,\"fixed\":false,"
                + "\"elementType\":{\"type\":\"OBJECT\",\"nullable\":true}} ;; "
                + "LOD_DIMENSION_TYPE|{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":67108864,"
                + "\"nullable\":true,\"fixed\":false} ;; ACCESS_MODIFIER|{\"type\":\"TEXT\",\"length\":7,"
                + "\"byteLength\":28,\"nullable\":true,\"fixed\":false}",
            "SELECT \"column_name\", \"data_type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        engine.execute("SHOW COLUMNS IN VIEW SNOWFLAKE.ACCOUNT_USAGE.APPLICATION_CONFIGURATIONS");
        assertAnswer("ID|{\"type\":\"FIXED\",\"precision\":38,\"scale\":0,\"nullable\":true} ;; "
                + "NAME|{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":16777216,\"nullable\":true,"
                + "\"fixed\":false} ;; APPLICATION_ID|{\"type\":\"FIXED\",\"precision\":38,\"scale\":0,"
                + "\"nullable\":true} ;; APPLICATION_NAME|{\"type\":\"TEXT\",\"length\":16777216,"
                + "\"byteLength\":16777216,\"nullable\":true,\"fixed\":false} ;; "
                + "CREATED_ON|{\"type\":\"TIMESTAMP_LTZ\",\"precision\":0,\"scale\":6,\"nullable\":true} ;;"
                + " MODIFIED_ON|{\"type\":\"TIMESTAMP_LTZ\",\"precision\":0,\"scale\":6,\"nullable\":true} "
                + ";; DELETED_ON|{\"type\":\"TIMESTAMP_LTZ\",\"precision\":0,\"scale\":6,\"nullable\":true}"
                + " ;; TYPE|{\"type\":\"TEXT\",\"length\":20,\"byteLength\":80,\"nullable\":true,"
                + "\"fixed\":false} ;; STATUS|{\"type\":\"TEXT\",\"length\":7,\"byteLength\":28,"
                + "\"nullable\":true,\"fixed\":false} ;; SENSITIVE|{\"type\":\"BOOLEAN\",\"nullable\":true}"
                + " ;; VALUE|{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":16777216,"
                + "\"nullable\":true,\"fixed\":false} ;; VALUE_UPDATED_ON|{\"type\":\"TIMESTAMP_LTZ\","
                + "\"precision\":0,\"scale\":6,\"nullable\":true} ;; LABEL|{\"type\":\"TEXT\","
                + "\"length\":16777216,\"byteLength\":16777216,\"nullable\":true,\"fixed\":false} ;; "
                + "DESCRIPTION|{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":16777216,"
                + "\"nullable\":true,\"fixed\":false} ;; APPLICATION_ROLES|{\"type\":\"TEXT\","
                + "\"length\":16777216,\"byteLength\":16777216,\"nullable\":false,\"fixed\":false} ;; "
                + "ADDITIONAL_PROPERTIES|{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":16777216,"
                + "\"nullable\":true,\"fixed\":false}",
            "SELECT \"column_name\", \"data_type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }

    /**
     * The columns a query answers as a bare NULL's {@code VARCHAR(0)}, and what that type folds and unites into;
     * a view or a table made over one declares the default text.
     */
    @Test
    public void answeredAsABareNull() {
        engine.execute("SELECT * FROM SNOWFLAKE.ACCOUNT_USAGE.TYPES WHERE 1 = 0");
        assertAnswer("TYPE_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "TYPE_NAME|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "TYPE_SCHEMA_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "TYPE_SCHEMA|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "TYPE_CATALOG_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "TYPE_CATALOG|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "TYPE_OWNER|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "OWNER_ROLE_TYPE|VARCHAR(13)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "BASE_DATA_TYPE|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "CHARACTER_MAXIMUM_LENGTH|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;;"
                + " CHARACTER_OCTET_LENGTH|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "NUMERIC_PRECISION|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "NUMERIC_PRECISION_RADIX|NUMBER(2,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "NUMERIC_SCALE|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "DATETIME_PRECISION|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "CHECK_EXPRESSION|VARCHAR(0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "DEFAULT_EXPRESSION|VARCHAR(0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "IS_NULLABLE_DEFAULT|VARCHAR(0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "COLLATION_NAME|VARCHAR(0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "CREATED|TIMESTAMP_LTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "LAST_ALTERED|TIMESTAMP_LTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "DELETED|TIMESTAMP_LTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "COMMENT|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE RESULT LAST_QUERY_ID()");
        engine.execute("SELECT * FROM SNOWFLAKE.ACCOUNT_USAGE.HYBRID_TABLE_USAGE_HISTORY WHERE 1 = 0");
        assertAnswer("OBJECT_TYPE|VARCHAR(7)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "OBJECT_ID|VARCHAR(0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "OBJECT_NAME|VARCHAR(0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "START_TIME|TIMESTAMP_LTZ(9)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "END_TIME|TIMESTAMP_LTZ(9)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "CREDITS_USED|NUMBER(38,9)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE RESULT LAST_QUERY_ID()");
        engine.execute("SELECT REGION, COALESCE(REGION, 'xy') AS c3, NVL(REGION, 'xy') AS c4, IFF(TRUE, REGION, "
            + "'ab') AS c5, REGION || 'a' AS c6 FROM SNOWFLAKE.ACCOUNT_USAGE.POLICY_REFERENCES WHERE 1 "
            + "= 0");
        assertAnswer("REGION|VARCHAR(0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "C3|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "C4|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "C5|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "C6|VARCHAR(134217728)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE RESULT LAST_QUERY_ID()");
        engine.execute("SELECT INTERVAL_TYPE FROM SNOWFLAKE.ACCOUNT_USAGE.COLUMNS UNION ALL SELECT 'abc'");
        assertAnswer("INTERVAL_TYPE|VARCHAR(3)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE RESULT LAST_QUERY_ID()");
        engine.execute("CREATE OR REPLACE VIEW v1 AS SELECT REGION FROM "
            + "SNOWFLAKE.ACCOUNT_USAGE.POLICY_REFERENCES");
        assertAnswer("REGION|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW v1");
        engine.execute("SELECT * FROM v1 WHERE 1 = 0");
        assertAnswer("REGION|VARCHAR(0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE RESULT LAST_QUERY_ID()");
        engine.execute("CREATE OR REPLACE TABLE t1 AS SELECT REGION FROM "
            + "SNOWFLAKE.ACCOUNT_USAGE.POLICY_REFERENCES WHERE 1 = 0");
        assertAnswer("REGION|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1");
    }

    /**
     * A user's SQL declares no {@code VARCHAR(0)}: a cast and a column are refused, a handler reading 1810 / 42601.
     */
    @Test
    public void userSqlDeclaresNoZeroWidthText() {
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 28 | Invalid character length: "
                + "0. Must be between 1 and 134,217,728.",
            "SELECT CAST(NULL AS VARCHAR(0))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 27 | Invalid character length: "
                + "0. Must be between 1 and 134,217,728.",
            "CREATE TABLE t0 (c VARCHAR(0))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 16 | Invalid character length: "
                + "0. Must be between 1 and 134,217,728.",
            "SELECT ''::CHAR(0)");
        assertAnswer("1810/42601",
            "EXECUTE IMMEDIATE $$ BEGIN EXECUTE IMMEDIATE 'SELECT CAST(NULL AS VARCHAR(0))'; RETURN "
            + "'no'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '/' || SQLSTATE; END; $$");
        assertAnswer("1810/42601",
            "EXECUTE IMMEDIATE $$ BEGIN EXECUTE IMMEDIATE 'CREATE TABLE t9 (c VARCHAR(0))'; RETURN "
            + "'no'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '/' || SQLSTATE; END; $$");
    }
}
