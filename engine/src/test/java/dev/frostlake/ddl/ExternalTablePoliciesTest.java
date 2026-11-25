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
 * Policies an external table takes when it is created: a masking policy in a column's definition and a row
 * access policy on VALUE, the refusals of anything else, their rows in POLICY_REFERENCES under the EXTERNAL_TABLE
 * domain, and the further columns a conditional masking policy reads (live-verified).
 */
public class ExternalTablePoliciesTest extends MeasuredCellsTestSupport {

    @Test
    public void attachmentsAreListed() {
        engine.execute("CREATE OR REPLACE STAGE st63 URL='s3://snowflake-workshop-lab/' FILE_FORMAT=(TYPE=CSV)");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY rp63 AS (v VARIANT) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE OR REPLACE MASKING POLICY mp63 AS (v VARCHAR) RETURNS VARCHAR -> v");
        engine.execute("CREATE OR REPLACE MASKING POLICY mp63c AS (v VARCHAR, w VARCHAR) RETURNS VARCHAR -> v");
        engine.execute("CREATE OR REPLACE EXTERNAL TABLE et4 (a VARCHAR AS (value:c1::varchar) WITH MASKING POLICY "
            + "mp63, b NUMBER AS (value:c2::number)) LOCATION=@st63 AUTO_REFRESH=FALSE "
            + "REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV) WITH ROW ACCESS POLICY rp63 ON (value)");
        engine.execute("CREATE OR REPLACE EXTERNAL TABLE et5 (a VARCHAR AS (value:c1::varchar) NOT NULL MASKING "
            + "POLICY mp63c USING (a, b), b VARCHAR AS (value:c2::varchar)) LOCATION=@st63 "
            + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV) ROW ACCESS POLICY rp63 ON "
            + "(value) COMMENT = 'x'");
        engine.execute("CREATE OR REPLACE EXTERNAL TABLE et6 LOCATION=@st63 AUTO_REFRESH=FALSE "
            + "REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV) WITH ROW ACCESS POLICY rp63 ON (VALUE)");
        engine.execute("CREATE OR REPLACE EXTERNAL TABLE et7 (a VARCHAR AS (value:c1::varchar)) LOCATION=@st63 "
            + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)");
        engine.execute("CREATE OR REPLACE TABLE rt63 (v VARIANT, s VARCHAR WITH MASKING POLICY mp63) WITH ROW ACCESS"
            + " POLICY rp63 ON (v)");
        engine.execute("CREATE OR REPLACE VIEW vw63 AS SELECT 1 AS x");
        assertCells(new String[][] {
            {"SELECT LISTAGG(IFF(policy_db = CURRENT_DATABASE() AND policy_schema = CURRENT_SCHEMA() AND "
                + "ref_database_name = CURRENT_DATABASE() AND ref_schema_name = CURRENT_SCHEMA(), 'here', "
                + "'elsewhere') || '/' || policy_name || '/' || policy_kind || '/' || ref_entity_name || '/' ||"
                + " ref_entity_domain || '/' || COALESCE(ref_column_name, '-') || '/' || "
                + "COALESCE(REPLACE(ref_arg_column_names, ', ', ','), '-') || '/' || policy_status, '; ') "
                + "WITHIN GROUP (ORDER BY policy_name, ref_entity_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'et4', REF_ENTITY_DOMAIN => "
                + "'external table'))",
                "here/MP63/MASKING_POLICY/ET4/EXTERNAL_TABLE/A/-/ACTIVE; "
                + "here/RP63/ROW_ACCESS_POLICY/ET4/EXTERNAL_TABLE/-/[ \"VALUE\" ]/ACTIVE"},
            {"SELECT LISTAGG(IFF(policy_db = CURRENT_DATABASE() AND policy_schema = CURRENT_SCHEMA() AND "
                + "ref_database_name = CURRENT_DATABASE() AND ref_schema_name = CURRENT_SCHEMA(), 'here', "
                + "'elsewhere') || '/' || policy_name || '/' || policy_kind || '/' || ref_entity_name || '/' ||"
                + " ref_entity_domain || '/' || COALESCE(ref_column_name, '-') || '/' || "
                + "COALESCE(REPLACE(ref_arg_column_names, ', ', ','), '-') || '/' || policy_status, '; ') "
                + "WITHIN GROUP (ORDER BY policy_name, ref_entity_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'et4', REF_ENTITY_DOMAIN => "
                + "'EXTERNAL_TABLE'))",
                "here/MP63/MASKING_POLICY/ET4/EXTERNAL_TABLE/A/-/ACTIVE; "
                + "here/RP63/ROW_ACCESS_POLICY/ET4/EXTERNAL_TABLE/-/[ \"VALUE\" ]/ACTIVE"},
            {"SELECT LISTAGG(IFF(policy_db = CURRENT_DATABASE() AND policy_schema = CURRENT_SCHEMA() AND "
                + "ref_database_name = CURRENT_DATABASE() AND ref_schema_name = CURRENT_SCHEMA(), 'here', "
                + "'elsewhere') || '/' || policy_name || '/' || policy_kind || '/' || ref_entity_name || '/' ||"
                + " ref_entity_domain || '/' || COALESCE(ref_column_name, '-') || '/' || "
                + "COALESCE(REPLACE(ref_arg_column_names, ', ', ','), '-') || '/' || policy_status, '; ') "
                + "WITHIN GROUP (ORDER BY policy_name, ref_entity_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'et4', REF_ENTITY_DOMAIN => "
                + "'table'))",
                "here/MP63/MASKING_POLICY/ET4/EXTERNAL_TABLE/A/-/ACTIVE; "
                + "here/RP63/ROW_ACCESS_POLICY/ET4/EXTERNAL_TABLE/-/[ \"VALUE\" ]/ACTIVE"},
            {"SELECT LISTAGG(IFF(policy_db = CURRENT_DATABASE() AND policy_schema = CURRENT_SCHEMA() AND "
                + "ref_database_name = CURRENT_DATABASE() AND ref_schema_name = CURRENT_SCHEMA(), 'here', "
                + "'elsewhere') || '/' || policy_name || '/' || policy_kind || '/' || ref_entity_name || '/' ||"
                + " ref_entity_domain || '/' || COALESCE(ref_column_name, '-') || '/' || "
                + "COALESCE(REPLACE(ref_arg_column_names, ', ', ','), '-') || '/' || policy_status, '; ') "
                + "WITHIN GROUP (ORDER BY policy_name, ref_entity_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'ET5', REF_ENTITY_DOMAIN => "
                + "'External Table'))",
                "here/MP63C/MASKING_POLICY/ET5/EXTERNAL_TABLE/A/[ \"B\" ]/ACTIVE; "
                + "here/RP63/ROW_ACCESS_POLICY/ET5/EXTERNAL_TABLE/-/[ \"VALUE\" ]/ACTIVE"},
            {"SELECT LISTAGG(IFF(policy_db = CURRENT_DATABASE() AND policy_schema = CURRENT_SCHEMA() AND "
                + "ref_database_name = CURRENT_DATABASE() AND ref_schema_name = CURRENT_SCHEMA(), 'here', "
                + "'elsewhere') || '/' || policy_name || '/' || policy_kind || '/' || ref_entity_name || '/' ||"
                + " ref_entity_domain || '/' || COALESCE(ref_column_name, '-') || '/' || "
                + "COALESCE(REPLACE(ref_arg_column_names, ', ', ','), '-') || '/' || policy_status, '; ') "
                + "WITHIN GROUP (ORDER BY policy_name, ref_entity_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'et6', REF_ENTITY_DOMAIN => "
                + "'external table'))",
                "here/RP63/ROW_ACCESS_POLICY/ET6/EXTERNAL_TABLE/-/[ \"VALUE\" ]/ACTIVE"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'et7', "
                + "REF_ENTITY_DOMAIN => 'external table'))",
                "0"},
            {"SELECT LISTAGG(IFF(policy_db = CURRENT_DATABASE() AND policy_schema = CURRENT_SCHEMA() AND "
                + "ref_database_name = CURRENT_DATABASE() AND ref_schema_name = CURRENT_SCHEMA(), 'here', "
                + "'elsewhere') || '/' || policy_name || '/' || policy_kind || '/' || ref_entity_name || '/' ||"
                + " ref_entity_domain || '/' || COALESCE(ref_column_name, '-') || '/' || "
                + "COALESCE(REPLACE(ref_arg_column_names, ', ', ','), '-') || '/' || policy_status, '; ') "
                + "WITHIN GROUP (ORDER BY policy_name, ref_entity_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(POLICY_NAME => 'rp63'))",
                "here/RP63/ROW_ACCESS_POLICY/ET4/EXTERNAL_TABLE/-/[ \"VALUE\" ]/ACTIVE; "
                + "here/RP63/ROW_ACCESS_POLICY/ET5/EXTERNAL_TABLE/-/[ \"VALUE\" ]/ACTIVE; "
                + "here/RP63/ROW_ACCESS_POLICY/ET6/EXTERNAL_TABLE/-/[ \"VALUE\" ]/ACTIVE; "
                + "here/RP63/ROW_ACCESS_POLICY/RT63/TABLE/-/[ \"V\" ]/ACTIVE"},
            {"SELECT LISTAGG(IFF(policy_db = CURRENT_DATABASE() AND policy_schema = CURRENT_SCHEMA() AND "
                + "ref_database_name = CURRENT_DATABASE() AND ref_schema_name = CURRENT_SCHEMA(), 'here', "
                + "'elsewhere') || '/' || policy_name || '/' || policy_kind || '/' || ref_entity_name || '/' ||"
                + " ref_entity_domain || '/' || COALESCE(ref_column_name, '-') || '/' || "
                + "COALESCE(REPLACE(ref_arg_column_names, ', ', ','), '-') || '/' || policy_status, '; ') "
                + "WITHIN GROUP (ORDER BY policy_name, ref_entity_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(POLICY_NAME => 'mp63'))",
                "here/MP63/MASKING_POLICY/ET4/EXTERNAL_TABLE/A/-/ACTIVE; "
                + "here/MP63/MASKING_POLICY/RT63/TABLE/S/-/ACTIVE"},
            {"SELECT LISTAGG(IFF(policy_db = CURRENT_DATABASE() AND policy_schema = CURRENT_SCHEMA() AND "
                + "ref_database_name = CURRENT_DATABASE() AND ref_schema_name = CURRENT_SCHEMA(), 'here', "
                + "'elsewhere') || '/' || policy_name || '/' || policy_kind || '/' || ref_entity_name || '/' ||"
                + " ref_entity_domain || '/' || COALESCE(ref_column_name, '-') || '/' || "
                + "COALESCE(REPLACE(ref_arg_column_names, ', ', ','), '-') || '/' || policy_status, '; ') "
                + "WITHIN GROUP (ORDER BY policy_name, ref_entity_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'rt63', REF_ENTITY_DOMAIN => "
                + "'external table'))",
                "here/MP63/MASKING_POLICY/RT63/TABLE/S/-/ACTIVE; here/RP63/ROW_ACCESS_POLICY/RT63/TABLE/-/[ "
                + "\"V\" ]/ACTIVE"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'vw63', "
                + "REF_ENTITY_DOMAIN => 'external table'))",
                "0"},
            {"SELECT COUNT(*) FROM et4",
                "0"},
            {"DROP ROW ACCESS POLICY rp63",
                "REFUSED SQL compilation error: Policy RP63 cannot be dropped/replaced as it is associated "
                + "with one or more entities."},
            {"DROP MASKING POLICY mp63c",
                "REFUSED SQL compilation error: Policy MP63C cannot be dropped/replaced as it is associated "
                + "with one or more entities."},
        });
    }

    @Test
    public void refusals() {
        engine.execute("CREATE OR REPLACE STAGE st63 URL='s3://snowflake-workshop-lab/' FILE_FORMAT=(TYPE=CSV)");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY rp63 AS (v VARIANT) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY rp63b AS (v VARCHAR) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY rp63c AS (v VARCHAR, w VARIANT) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE OR REPLACE MASKING POLICY mp63 AS (v VARCHAR) RETURNS VARCHAR -> v");
        engine.execute("CREATE OR REPLACE MASKING POLICY mp63n AS (v NUMBER) RETURNS NUMBER -> v");
        engine.execute("CREATE OR REPLACE EXTERNAL TABLE kept63 (a VARCHAR AS (value:c1::varchar)) LOCATION=@st63 "
            + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV) WITH ROW ACCESS POLICY "
            + "rp63 ON (value)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE EXTERNAL TABLE et1 (a VARCHAR AS (value:c1::varchar)) WITH ROW ACCESS "
                + "POLICY rp63b ON (a) LOCATION=@st63 AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE "
                + "FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: Row access policy cannot be attached to a VIRTUAL_COLUMN "
                + "column."},
            {"CREATE OR REPLACE EXTERNAL TABLE et2 (a VARCHAR AS (value:c1::varchar)) LOCATION=@st63 "
                + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV) WITH ROW ACCESS POLICY "
                + "rp63 ON (a, value)",
                "REFUSED SQL compilation error: Row access policy cannot be attached to a VIRTUAL_COLUMN "
                + "column."},
            {"CREATE OR REPLACE EXTERNAL TABLE et3 (a VARCHAR AS (value:c1::varchar)) LOCATION=@st63 "
                + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV) WITH ROW ACCESS POLICY "
                + "rp63c ON (zz, a)",
                "REFUSED SQL compilation error: | column 'ZZ' does not exist"},
            {"CREATE OR REPLACE EXTERNAL TABLE et4 (a VARCHAR AS (value:c1::varchar)) LOCATION=@st63 "
                + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV) WITH ROW ACCESS POLICY "
                + "rp63c ON (value)",
                "REFUSED SQL compilation error: | Invalid number of arguments for attaching policy 'RP63C' to"
                + " 'ET4', expected 2 arguments, got 1 arguments."},
            {"CREATE OR REPLACE EXTERNAL TABLE et5 (a VARCHAR AS (value:c1::varchar)) LOCATION=@st63 "
                + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV) WITH ROW ACCESS POLICY "
                + "rp63b ON (VALUE)",
                "REFUSED SQL compilation error: Column 'VALUE' data type 'VARIANT' does not match with Row "
                + "access policy data type 'VARCHAR(134217728)'."},
            {"CREATE OR REPLACE EXTERNAL TABLE et6 (a VARCHAR AS (value:c1::varchar)) LOCATION=@st63 "
                + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV) WITH ROW ACCESS POLICY "
                + "rp63b ON (zz)",
                "REFUSED SQL compilation error: | column 'ZZ' does not exist"},
            {"CREATE OR REPLACE EXTERNAL TABLE et7 (a VARCHAR AS (value:c1::varchar)) WITH ROW ACCESS "
                + "POLICY rp63 ON (VALUE) WITH ROW ACCESS POLICY rp63 ON (VALUE) LOCATION=@st63 "
                + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED Cannot attach more than one Row access policy to a External table."},
            {"CREATE OR REPLACE EXTERNAL TABLE et8 (a VARCHAR AS (value:c1::varchar)) WITH ROW ACCESS "
                + "POLICY rp63 ON (value) WITH ROW ACCESS POLICY rp63 ON (value)",
                "REFUSED Cannot attach more than one Row access policy to a External table."},
            {"CREATE OR REPLACE EXTERNAL TABLE et9 (a VARCHAR AS (value:c1::varchar)) AUTO_REFRESH=FALSE "
                + "FILE_FORMAT=(TYPE=CSV) WITH ROW ACCESS POLICY rp63 ON (value)",
                "REFUSED SQL compilation error: | Missing option(s): location"},
            {"CREATE OR REPLACE EXTERNAL TABLE et10 (a VARCHAR AS (value:c1::varchar) WITH MASKING POLICY "
                + "mp63n) LOCATION=@st63 AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: COLUMN data type VARCHAR(16777216) does not match with "
                + "masking policy data type FIXED."},
            {"CREATE OR REPLACE EXTERNAL TABLE et11 (a VARCHAR AS (value:c1::varchar) WITH MASKING POLICY "
                + "mp63 USING (a, zz)) LOCATION=@st63 AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE "
                + "FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | column 'ZZ' does not exist"},
            {"CREATE OR REPLACE EXTERNAL TABLE et12 (a VARCHAR AS (value:c1::varchar) WITH MASKING POLICY "
                + "mp63 USING (a, value)) LOCATION=@st63 AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE "
                + "FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | Invalid number of arguments for attaching policy 'MP63' to "
                + "'ET12', expected 1 arguments, got 2 arguments."},
            {"CREATE OR REPLACE EXTERNAL TABLE kept63 (a VARCHAR AS (value:c1::varchar)) LOCATION=@st63 "
                + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV) WITH ROW ACCESS POLICY "
                + "rp63b ON (value)",
                "REFUSED SQL compilation error: Column 'VALUE' data type 'VARIANT' does not match with Row "
                + "access policy data type 'VARCHAR(134217728)'."},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'kept63',"
                + " REF_ENTITY_DOMAIN => 'external table'))",
                "1"},
            {"ALTER EXTERNAL TABLE kept63 ADD ROW ACCESS POLICY rp63 ON (VALUE)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'ROW'."},
            {"ALTER EXTERNAL TABLE kept63 ADD ROW ACCESS POLICY rp63b ON (a)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'ROW'."},
            {"ALTER EXTERNAL TABLE kept63 DROP ROW ACCESS POLICY rp63",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'ROW'."},
            {"ALTER EXTERNAL TABLE kept63 MODIFY COLUMN a SET MASKING POLICY mp63",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'MODIFY'."},
            {"ALTER EXTERNAL TABLE kept63 DROP ALL ROW ACCESS POLICIES",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'ALL'."},
            {"ALTER MATERIALIZED VIEW mv63 ADD ROW ACCESS POLICY rp63 ON (a)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'ADD'."},
        });
    }

    @Test
    public void conditionalMaskingArguments() {
        engine.execute("CREATE OR REPLACE MASKING POLICY mp63c AS (v VARCHAR, w VARCHAR) RETURNS VARCHAR -> v");
        engine.execute("CREATE OR REPLACE MASKING POLICY mp63d AS (v VARCHAR, w VARCHAR, x NUMBER) RETURNS VARCHAR "
            + "-> v");
        engine.execute("CREATE OR REPLACE TABLE rt1 (a VARCHAR WITH MASKING POLICY mp63c USING (a, b), b VARCHAR, c "
            + "NUMBER)");
        engine.execute("CREATE OR REPLACE TABLE rt2 (a VARCHAR, b VARCHAR, c NUMBER)");
        engine.execute("ALTER TABLE rt2 MODIFY COLUMN a SET MASKING POLICY mp63d USING (a, b, c)");
        engine.execute("CREATE OR REPLACE VIEW rv1 (a WITH MASKING POLICY mp63c USING (a, b), b) AS SELECT 'x' AS a,"
            + " 'y' AS b");
        assertCells(new String[][] {
            {"SELECT LISTAGG(IFF(policy_db = CURRENT_DATABASE() AND policy_schema = CURRENT_SCHEMA() AND "
                + "ref_database_name = CURRENT_DATABASE() AND ref_schema_name = CURRENT_SCHEMA(), 'here', "
                + "'elsewhere') || '/' || policy_name || '/' || policy_kind || '/' || ref_entity_name || '/' ||"
                + " ref_entity_domain || '/' || COALESCE(ref_column_name, '-') || '/' || "
                + "COALESCE(REPLACE(ref_arg_column_names, ', ', ','), '-') || '/' || policy_status, '; ') "
                + "WITHIN GROUP (ORDER BY policy_name, ref_entity_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'rt1', REF_ENTITY_DOMAIN => "
                + "'table'))",
                "here/MP63C/MASKING_POLICY/RT1/TABLE/A/[ \"B\" ]/ACTIVE"},
            {"SELECT LISTAGG(IFF(policy_db = CURRENT_DATABASE() AND policy_schema = CURRENT_SCHEMA() AND "
                + "ref_database_name = CURRENT_DATABASE() AND ref_schema_name = CURRENT_SCHEMA(), 'here', "
                + "'elsewhere') || '/' || policy_name || '/' || policy_kind || '/' || ref_entity_name || '/' ||"
                + " ref_entity_domain || '/' || COALESCE(ref_column_name, '-') || '/' || "
                + "COALESCE(REPLACE(ref_arg_column_names, ', ', ','), '-') || '/' || policy_status, '; ') "
                + "WITHIN GROUP (ORDER BY policy_name, ref_entity_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'rt2', REF_ENTITY_DOMAIN => "
                + "'table'))",
                "here/MP63D/MASKING_POLICY/RT2/TABLE/A/[ \"B\",\"C\" ]/ACTIVE"},
            {"SELECT LISTAGG(IFF(policy_db = CURRENT_DATABASE() AND policy_schema = CURRENT_SCHEMA() AND "
                + "ref_database_name = CURRENT_DATABASE() AND ref_schema_name = CURRENT_SCHEMA(), 'here', "
                + "'elsewhere') || '/' || policy_name || '/' || policy_kind || '/' || ref_entity_name || '/' ||"
                + " ref_entity_domain || '/' || COALESCE(ref_column_name, '-') || '/' || "
                + "COALESCE(REPLACE(ref_arg_column_names, ', ', ','), '-') || '/' || policy_status, '; ') "
                + "WITHIN GROUP (ORDER BY policy_name, ref_entity_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'rv1', REF_ENTITY_DOMAIN => "
                + "'view'))",
                "here/MP63C/MASKING_POLICY/RV1/VIEW/A/[ \"B\" ]/ACTIVE"},
            {"SELECT LISTAGG(IFF(policy_db = CURRENT_DATABASE() AND policy_schema = CURRENT_SCHEMA() AND "
                + "ref_database_name = CURRENT_DATABASE() AND ref_schema_name = CURRENT_SCHEMA(), 'here', "
                + "'elsewhere') || '/' || policy_name || '/' || policy_kind || '/' || ref_entity_name || '/' ||"
                + " ref_entity_domain || '/' || COALESCE(ref_column_name, '-') || '/' || "
                + "COALESCE(REPLACE(ref_arg_column_names, ', ', ','), '-') || '/' || policy_status, '; ') "
                + "WITHIN GROUP (ORDER BY policy_name, ref_entity_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(POLICY_NAME => 'mp63c'))",
                "here/MP63C/MASKING_POLICY/RT1/TABLE/A/[ \"B\" ]/ACTIVE; "
                + "here/MP63C/MASKING_POLICY/RV1/VIEW/A/[ \"B\" ]/ACTIVE"},
        });
    }
}
