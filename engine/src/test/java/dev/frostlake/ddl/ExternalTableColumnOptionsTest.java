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
 * An external table's columns and listings (live-verified): a column takes NULL or NOT NULL any number of times on
 * either side of its expression, then a masking policy, tags and a comment in that order, a WITH after the tags
 * refusing the word after it; VALUE's comment is "The value of this row"; a column's expression is told back
 * resolved over VALUE, {@code value:c1::varchar} as {@code CAST(GET(VALUE, 'c1') AS VARCHAR)}; a column with no
 * expression, a tag given two values and an internal stage are refused in the account's words and codes; a fault in
 * the column list reports nothing inside FILE_FORMAT's brackets after it; and SHOW EXTERNAL TABLES names the stage
 * with its '@' and the location as the stage's URL and the path written after it.
 */
public class ExternalTableColumnOptionsTest extends MeasuredCellsTestSupport {

    @Test
    public void columnOptions() {
        engine.execute("CREATE OR REPLACE STAGE st URL='s3://snowflake-workshop-lab/' FILE_FORMAT=(TYPE=CSV)");
        engine.execute("CREATE OR REPLACE STAGE si");
        engine.execute("CREATE OR REPLACE MASKING POLICY mp AS (v VARCHAR) RETURNS VARCHAR -> v");
        engine.execute("CREATE OR REPLACE TAG t1");
        engine.execute("CREATE OR REPLACE TAG t2");
        engine.execute("CREATE OR REPLACE EXTERNAL TABLE co1 (a VARCHAR AS (value:c1::varchar) NOT NULL WITH MASKING"
            + " POLICY mp WITH TAG (t1='y', t2='z') COMMENT 'cc', b NUMBER AS (value:c2::number) TAG "
            + "(t2='w') COMMENT $$b$$, c VARCHAR NULL AS (value:c3::varchar) NULL NOT NULL) LOCATION=@st "
            + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)");
        engine.execute("CREATE OR REPLACE EXTERNAL TABLE co2 (a VARCHAR AS (value:c1::varchar) NULL MASKING POLICY "
            + "mp COMMENT 'k') LOCATION=@st AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE "
            + "FILE_FORMAT=(TYPE=CSV)");
        assertCells(new String[][] {
            {"DESC EXTERNAL TABLE co1 ->> SELECT LISTAGG(\"name\" || '/' || \"null?\" || '/' || "
                + "COALESCE(\"comment\", '-') || '/' || COALESCE(\"policy name\", '-'), ' | ') FROM $1",
                "VALUE/Y/The value of this row/- | A/N/cc/TEST_DB.TEST_SCHEMA.MP | B/Y/b/- | C/N/-/-"},
            {"DESC TABLE co2 ->> SELECT LISTAGG(\"name\" || '/' || \"null?\" || '/' || "
                + "COALESCE(\"comment\", '-') || '/' || COALESCE(\"policy name\", '-'), ' | ') FROM $1",
                "VALUE/Y/The value of this row/- | A/Y/k/TEST_DB.TEST_SCHEMA.MP"},
            {"SELECT LISTAGG(column_name || '=' || COALESCE(comment, '-') || '/' || is_nullable, '; ') "
                + "WITHIN GROUP (ORDER BY ordinal_position) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_name = "
                + "'CO1'",
                "VALUE=The value of this row/YES; A=cc/NO; B=b/YES; C=-/NO"},
            {"SHOW COLUMNS IN TABLE co1 ->> SELECT LISTAGG(\"column_name\" || '/' || \"kind\" || '/' || "
                + "COALESCE(\"comment\", '-'), ' | ') WITHIN GROUP (ORDER BY \"column_name\") FROM $1",
                "A/VIRTUAL_COLUMN/cc | B/VIRTUAL_COLUMN/b | C/VIRTUAL_COLUMN/ | VALUE/COLUMN/The value of "
                + "this row"},
            {"SELECT LISTAGG(tag_name || '=' || tag_value || '/' || level || '/' || COALESCE(column_name, "
                + "'-'), '; ') WITHIN GROUP (ORDER BY column_name, tag_name) FROM "
                + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('co1', 'table'))",
                "T1=y/COLUMN/A; T2=z/COLUMN/A; T2=w/COLUMN/B"},
            {"SELECT SYSTEM$GET_TAG('t2', 'co1.b', 'column')",
                "w"},
            {"CREATE OR REPLACE EXTERNAL TABLE r1 (a VARCHAR AS (value:c1::varchar) WITH TAG (t1='y', "
                + "t1='z')) LOCATION=@st AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED Same tag T1 with multiple values provided, values = y, z"},
            {"CREATE OR REPLACE EXTERNAL TABLE r1 (b VARCHAR AS (value:c1::varchar), a VARCHAR NOT NULL) "
                + "LOCATION=@st AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED External table column A must have a defining expression."},
            {"CREATE OR REPLACE EXTERNAL TABLE r1 (a VARCHAR COMMENT 'a') LOCATION=@st AUTO_REFRESH=FALSE "
                + "REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED External table column A must have a defining expression."},
            {"CREATE OR REPLACE EXTERNAL TABLE r1 (a VARCHAR) LOCATION=@si FILE_FORMAT=(TYPE=CSV)",
                "REFUSED Cannot use internal stage SI as the location for an external table."},
            {"CREATE OR REPLACE EXTERNAL TABLE r1 (a VARCHAR AS (value:c1::varchar) COMMENT = 'x') "
                + "LOCATION=@st AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 78 unexpected '='."},
            {"CREATE OR REPLACE EXTERNAL TABLE r1 (a VARCHAR AS (value:c1::varchar) COMMENT 5) "
                + "LOCATION=@st AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 78 unexpected '5'."},
        });
    }

    @Test
    public void syntaxLines() {
        engine.execute("CREATE OR REPLACE STAGE st URL='s3://snowflake-workshop-lab/' FILE_FORMAT=(TYPE=CSV)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE EXTERNAL TABLE m5 (a VARCHAR AS (value:c1::varchar) NOT NULL MASKING "
                + "POLICY mp NOT NULL) LOCATION=@st AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE "
                + "FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 97 unexpected 'NOT'."},
            {"CREATE OR REPLACE EXTERNAL TABLE m6 (a VARCHAR AS (value:c1::varchar) NOT NOT NULL) "
                + "LOCATION=@st AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected 'NOT'."},
            {"CREATE OR REPLACE EXTERNAL TABLE k1 (a VARCHAR AS (value:c1::varchar) WITH TAG (t1='y') WITH"
                + " COMMENT 'x') LOCATION=@st AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 93 unexpected 'COMMENT'."},
            {"CREATE OR REPLACE EXTERNAL TABLE k2 (a VARCHAR AS (value:c1::varchar) WITH COMMENT 'x') "
                + "LOCATION=@st AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 75 unexpected 'COMMENT'."},
            {"CREATE OR REPLACE TABLE k3 (a VARCHAR WITH COMMENT 'x')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'COMMENT'."},
            {"CREATE OR REPLACE EXTERNAL TABLE k4 (a VARCHAR AS (value:c1::varchar) WITH TAG (t1='y') "
                + "WITH) LOCATION=@st AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 92 unexpected ')'."},
            {"CREATE OR REPLACE EXTERNAL TABLE k4 (a VARCHAR AS (value:c1::varchar) WITH TAG (t1='y') "
                + "WITH, b INT AS (value:c2::int)) LOCATION=@st AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE "
                + "FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 92 unexpected ','."},
            {"CREATE OR REPLACE EXTERNAL TABLE k4 (a VARCHAR AS (value:c1::varchar) WITH) LOCATION=@st "
                + "FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected ')'."},
            {"CREATE OR REPLACE EXTERNAL TABLE k4 (a VARCHAR AS (value:c1::varchar) WITH MASKING POLICY mp"
                + " WITH) LOCATION=@st FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 97 unexpected ')'."},
            {"CREATE OR REPLACE EXTERNAL TABLE k4 (a VARCHAR AS (value:c1::varchar) WITH MASKING POLICY mp"
                + " WITH COMMENT 'x') LOCATION=@st FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 98 unexpected 'COMMENT'."},
            {"CREATE OR REPLACE EXTERNAL TABLE k5 (\n a VARCHAR AS (value:c1::varchar) COMMENT 'x' NOT "
                + "NULL\n) LOCATION=@st\nFILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | syntax error line 2 at position 46 unexpected 'NOT'."},
            {"CREATE OR REPLACE EXTERNAL TABLE x1 (a VARCHAR AS (value:c1::varchar) WITH MASKING POLICY mp"
                + " NOT NULL) LOCATION=@st AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 93 unexpected 'NOT'."},
            {"CREATE OR REPLACE EXTERNAL TABLE x1 (a VARCHAR AS (value:c1::varchar) WITH MASKING POLICY mp"
                + " NOT NULL) LOCATION=@st FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 93 unexpected 'NOT'."},
            {"CREATE OR REPLACE EXTERNAL TABLE x1 (a VARCHAR AS (value:c1::varchar),) LOCATION=@st "
                + "FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 70 unexpected ')'."},
            {"CREATE OR REPLACE EXTERNAL TABLE x1 (a VARCHAR AS) LOCATION=@st FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected ')'."},
            {"CREATE OR REPLACE EXTERNAL TABLE x1 (a VARCHAR AS (value:c1::varchar) LOCATION=@st "
                + "FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 70 unexpected 'LOCATION'."},
            {"CREATE OR REPLACE EXTERNAL TABLE x1 (a VARCHAR AS (value:c1::varchar) WITH TAG t1='x') "
                + "LOCATION=@st FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 79 unexpected 't1'. | "
                + "syntax error line 1 at position 87 unexpected 'LOCATION'."},
            {"CREATE OR REPLACE EXTERNAL TABLE f1 (a VARCHAR AS (value:c1::varchar) WITH TAG (t1='y') WITH"
                + " foo) LOCATION=@st AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 93 unexpected 'foo'."},
            {"CREATE OR REPLACE EXTERNAL TABLE f1 (a VARCHAR AS (value:c1::varchar) WITH PROJECTION POLICY"
                + " pp) LOCATION=@st AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 75 unexpected 'PROJECTION'."},
            {"CREATE OR REPLACE EXTERNAL TABLE e5 (a VARCHAR AS (value:c1::varchar) COMMENT 'cc' NOT NULL)"
                + " LOCATION=@st AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 83 unexpected 'NOT'."},
            {"CREATE OR REPLACE EXTERNAL TABLE e7 (a VARCHAR AS (value:c1::varchar) WITH TAG (t1='y') NOT "
                + "NULL) LOCATION=@st AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 88 unexpected 'NOT'."},
            {"CREATE OR REPLACE EXTERNAL TABLE e11 (a VARCHAR AS (value:c1::varchar) WITH TAG (t1='y') "
                + "WITH MASKING POLICY mp) LOCATION=@st AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE "
                + "FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected 'MASKING'."},
        });
    }

    @Test
    public void describedExpressions() {
        engine.execute("CREATE OR REPLACE STAGE st URL='s3://snowflake-workshop-lab/' FILE_FORMAT=(TYPE=CSV)");
        engine.execute("CREATE OR REPLACE EXTERNAL TABLE d2 (a INT AS (value:c1::int), b VARCHAR(10) AS "
            + "(value:c2::varchar(10)), c DOUBLE AS (value:c3::double), d BOOLEAN AS (value:c4::boolean), e"
            + " TIMESTAMP_NTZ AS (value:c5::timestamp_ntz), f VARIANT AS (value:a.b), g VARIANT AS "
            + "(value:\"A\"), i VARCHAR AS (value:c1::varchar || 'x'), j INT AS (value:c1::int + 1), k "
            + "NUMBER(10,2) AS (value:c1::number(10,2)), l VARCHAR AS (CAST(value:c1 AS VARCHAR)), m "
            + "VARIANT AS (value[0]), n VARCHAR AS (TO_VARCHAR(value:c1)), o DATE AS (value:c1::date), p "
            + "VARCHAR AS (metadata$file_row_number::varchar), q VARIANT AS (value:c1:c2), t VARCHAR AS "
            + "(IFF(value:c1::int > 1, 'a', 'b')), u VARIANT AS (value), v VARIANT AS (VALUE:C1), x NUMBER "
            + "AS (value:c1::decimal(5,1)), y VARCHAR AS (value:c1::string), z VARCHAR AS "
            + "(LOWER(value:c1::varchar)), za VARCHAR AS (metadata$filename), zb DATE AS "
            + "TO_DATE(value:c3::string), zc VARCHAR AS (UPPER(value:c5)), zd VARCHAR AS "
            + "(value['c1']::varchar)) LOCATION=@st AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE "
            + "FILE_FORMAT=(TYPE=CSV)");
        assertCells(new String[][] {

        });
    }

    @Test
    public void listingCells() {
        engine.execute("CREATE OR REPLACE STAGE st URL='s3://snowflake-workshop-lab/' FILE_FORMAT=(TYPE=CSV)");
        engine.execute("CREATE OR REPLACE STAGE sn URL='s3://snowflake-workshop-lab/weather-nyc'");
        engine.execute("CREATE OR REPLACE STAGE sa URL='azure://myacct.blob.core.windows.net/c/'");
        engine.execute("CREATE OR REPLACE EXTERNAL TABLE h1 (a VARCHAR AS (value:c1::varchar)) LOCATION=@sn "
            + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)");
        engine.execute("CREATE OR REPLACE EXTERNAL TABLE h2 (a VARCHAR AS (value:c1::varchar)) LOCATION=@sn/x "
            + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)");
        engine.execute("CREATE OR REPLACE EXTERNAL TABLE h3 (a VARCHAR AS (value:c1::varchar)) LOCATION=@st/a//b/ "
            + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)");
        engine.execute("CREATE OR REPLACE EXTERNAL TABLE h4 (a VARCHAR AS (value:c1::varchar)) "
            + "LOCATION=@\"TEST_DB\".\"TEST_SCHEMA\".\"ST\"/q.csv AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE "
            + "FILE_FORMAT=(TYPE=CSV)");
        engine.execute("CREATE OR REPLACE EXTERNAL TABLE h5 (a VARCHAR AS (value:c1::varchar)) LOCATION=@test_schema.st/ "
            + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)");
        engine.execute("CREATE OR REPLACE EXTERNAL TABLE h6 (a VARCHAR AS (value:c1::varchar)) LOCATION=@sa/p/q "
            + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)");
        assertCells(new String[][] {
            {"SHOW EXTERNAL TABLES ->> SELECT LISTAGG(\"name\" || ' ' || \"stage\" || ' ' || \"location\" "
                + "|| ' ' || \"cloud\", ' | ') WITHIN GROUP (ORDER BY \"name\") FROM $1",
                "H1 @TEST_DB.TEST_SCHEMA.SN s3://snowflake-workshop-lab/weather-nyc AWS | H2 @TEST_DB.TEST_SCHEMA.SN "
                + "s3://snowflake-workshop-lab/weather-nyc/x/ AWS | H3 @TEST_DB.TEST_SCHEMA.ST "
                + "s3://snowflake-workshop-lab/a//b/ AWS | H4 @TEST_DB.TEST_SCHEMA.ST "
                + "s3://snowflake-workshop-lab/q.csv/ AWS | H5 @TEST_DB.TEST_SCHEMA.ST s3://snowflake-workshop-lab/ "
                + "AWS | H6 @TEST_DB.TEST_SCHEMA.SA azure://myacct.blob.core.windows.net/c/p/q/ AZURE"},
            {"SHOW TERSE EXTERNAL TABLES ->> SELECT LISTAGG(\"name\" || ' ' || \"kind\", ' | ') WITHIN "
                + "GROUP (ORDER BY \"name\") FROM $1",
                "H1 EXTERNAL_TABLE | H2 EXTERNAL_TABLE | H3 EXTERNAL_TABLE | H4 EXTERNAL_TABLE | H5 "
                + "EXTERNAL_TABLE | H6 EXTERNAL_TABLE"},
        });
    }
}
