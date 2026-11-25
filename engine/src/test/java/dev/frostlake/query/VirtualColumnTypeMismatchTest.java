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

import org.junit.jupiter.api.Test;

/**
 * A virtual column whose expression's type its declared type does not take is refused with 1482 / 42601 at the
 * column's name - in CREATE TABLE, ALTER TABLE … ADD COLUMN, CREATE EXTERNAL TABLE (where any width of the same
 * type is taken) and CREATE TABLE … LIKE (at no place) - and an invalid-type sentence quotes a text operand of an
 * arithmetic or a comparison with the conversion the plan gives it (live-verified).
 */
public class VirtualColumnTypeMismatchTest extends MeasuredCellsTestSupport {

    @Test
    public void tableColumns() {
        engine.execute("CREATE OR REPLACE TABLE ta (a NUMBER(38,2))");
        engine.execute("CREATE OR REPLACE TABLE src (a INT, b NUMBER(7,6) AS (1/a))");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE t1 (a NUMBER(38,2), b NUMBER(38,6) AS (a))",
                "REFUSED SQL compilation error: error line 1 at position 44 | Data type of virtual column "
                + "does not match the data type of its expression for column 'B'. Expected data type "
                + "'NUMBER(38,6)', found 'NUMBER(38,2)'"},
            {"CREATE OR REPLACE TABLE t2 (a INT, b VARCHAR AS (a))",
                "REFUSED SQL compilation error: error line 1 at position 35 | Data type of virtual column "
                + "does not match the data type of its expression for column 'B'. Expected data type "
                + "'VARCHAR(16777216)', found 'NUMBER(38,0)'"},
            {"CREATE OR REPLACE TABLE t3 (a VARCHAR(10), b VARCHAR(5) AS (a))",
                "REFUSED SQL compilation error: error line 1 at position 43 | Data type of virtual column "
                + "does not match the data type of its expression for column 'B'. Expected data type "
                + "'VARCHAR(5)', found 'VARCHAR(10)'"},
            {"CREATE OR REPLACE TABLE t4 (a NUMBER(10,2), b NUMBER(9,2) AS (a))",
                "REFUSED SQL compilation error: error line 1 at position 44 | Data type of virtual column "
                + "does not match the data type of its expression for column 'B'. Expected data type "
                + "'NUMBER(9,2)', found 'NUMBER(10,2)'"},
            {"CREATE OR REPLACE TABLE t5 (a INT,\n  b DATE AS (a))",
                "REFUSED SQL compilation error: error line 2 at position 2 | Data type of virtual column does"
                + " not match the data type of its expression for column 'B'. Expected data type 'DATE', found "
                + "'NUMBER(38,0)'"},
            {"CREATE OR REPLACE TABLE t6 (a FLOAT, b NUMBER AS (a))",
                "REFUSED SQL compilation error: error line 1 at position 37 | Data type of virtual column "
                + "does not match the data type of its expression for column 'B'. Expected data type "
                + "'NUMBER(38,0)', found 'FLOAT'"},
            {"CREATE OR REPLACE TABLE t7 (a INT, b FLOAT AS (a))",
                "REFUSED SQL compilation error: error line 1 at position 35 | Data type of virtual column "
                + "does not match the data type of its expression for column 'B'. Expected data type 'FLOAT', "
                + "found 'NUMBER(38,0)'"},
            {"CREATE OR REPLACE TABLE t8 (a TIMESTAMP_NTZ(3), b TIMESTAMP_NTZ(9) AS (a))",
                "REFUSED SQL compilation error: error line 1 at position 48 | Data type of virtual column "
                + "does not match the data type of its expression for column 'B'. Expected data type "
                + "'TIMESTAMP_NTZ(9)', found 'TIMESTAMP_NTZ(3)'"},
            {"ALTER TABLE ta ADD COLUMN b NUMBER(38,6) AS (a)",
                "REFUSED SQL compilation error: error line 1 at position 26 | Data type of virtual column "
                + "does not match the data type of its expression for column 'B'. Expected data type "
                + "'NUMBER(38,6)', found 'NUMBER(38,2)'"},
            {"ALTER TABLE ta ADD COLUMN c VARCHAR AS (a)",
                "REFUSED SQL compilation error: error line 1 at position 26 | Data type of virtual column "
                + "does not match the data type of its expression for column 'C'. Expected data type "
                + "'VARCHAR(16777216)', found 'NUMBER(38,2)'"},
            {"CREATE OR REPLACE TABLE dst LIKE src",
                "REFUSED SQL compilation error: error line 0 at position -1 | Data type of virtual column "
                + "does not match the data type of its expression for column 'B'. Expected data type "
                + "'NUMBER(7,6)', found 'NUMBER(13,12)'"},
            {"EXECUTE IMMEDIATE $$BEGIN CREATE OR REPLACE TABLE th (a INT, b VARCHAR AS (a)); RETURN 'ok';"
                + " EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END;$$",
                "1482 42601"},
            {"EXECUTE IMMEDIATE $$BEGIN CREATE OR REPLACE TABLE dst2 LIKE src; RETURN 'ok'; EXCEPTION WHEN"
                + " OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END;$$",
                "1482 42601"},
        });
    }

    @Test
    public void externalTableColumns() {
        engine.execute("CREATE OR REPLACE STAGE st63 URL='s3://snowflake-workshop-lab/' FILE_FORMAT=(TYPE=CSV)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE EXTERNAL TABLE x7 (a TIMESTAMP_LTZ AS (value:c1::timestamp_ntz)) "
                + "LOCATION=@st63 AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 37 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type "
                + "'TIMESTAMP_LTZ(9)', found 'TIMESTAMP_NTZ(9)'"},
            {"CREATE OR REPLACE EXTERNAL TABLE x9 (a VARIANT AS (value:c1::varchar)) LOCATION=@st63 "
                + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 37 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type 'VARIANT',"
                + " found 'VARCHAR(134217728)'"},
            {"CREATE OR REPLACE EXTERNAL TABLE x10 (a VARIANT AS (value:c1::int)) LOCATION=@st63 "
                + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 38 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type 'VARIANT',"
                + " found 'NUMBER(38,0)'"},
            {"CREATE OR REPLACE EXTERNAL TABLE x11 (a OBJECT AS (value)) LOCATION=@st63 AUTO_REFRESH=FALSE"
                + " REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 38 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type 'OBJECT', "
                + "found 'VARIANT'"},
            {"CREATE OR REPLACE EXTERNAL TABLE x12 (a ARRAY AS (value)) LOCATION=@st63 AUTO_REFRESH=FALSE "
                + "REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 38 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type 'ARRAY', "
                + "found 'VARIANT'"},
            {"CREATE OR REPLACE EXTERNAL TABLE x16 (a NUMBER AS (value:c1::float)) LOCATION=@st63 "
                + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 38 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type "
                + "'NUMBER(38,0)', found 'FLOAT'"},
            {"CREATE OR REPLACE EXTERNAL TABLE x17 (a VARCHAR AS (value:c1::int)) LOCATION=@st63 "
                + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 38 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type "
                + "'VARCHAR(16777216)', found 'NUMBER(38,0)'"},
            {"CREATE OR REPLACE EXTERNAL TABLE x20 (a DECFLOAT AS (value:c1::number)) LOCATION=@st63 "
                + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 38 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type "
                + "'DECFLOAT(38)', found 'NUMBER(38,0)'"},
            {"CREATE OR REPLACE EXTERNAL TABLE x21 (a NUMBER(38,0) AS (value:c1::decfloat)) LOCATION=@st63"
                + " AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 38 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type "
                + "'NUMBER(38,0)', found 'DECFLOAT(38)'"},
            {"CREATE OR REPLACE EXTERNAL TABLE x24 (a INT AS (value:c1)) LOCATION=@st63 AUTO_REFRESH=FALSE"
                + " REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 38 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type "
                + "'NUMBER(38,0)', found 'VARIANT'"},
            {"CREATE OR REPLACE EXTERNAL TABLE x25 (a DATE AS (value:c1::timestamp_ntz)) LOCATION=@st63 "
                + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 38 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type 'DATE', "
                + "found 'TIMESTAMP_NTZ(9)'"},
            {"CREATE OR REPLACE EXTERNAL TABLE x27 (a INT AS (zz)) LOCATION=@st63 AUTO_REFRESH=FALSE "
                + "REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 48 | invalid identifier 'ZZ'"},
            {"CREATE OR REPLACE EXTERNAL TABLE x28 (a INT AS (metadata$filename)) LOCATION=@st63 "
                + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 38 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type "
                + "'NUMBER(38,0)', found 'VARCHAR(16777216)'"},
            {"CREATE OR REPLACE EXTERNAL TABLE x29 (a VARCHAR AS (metadata$file_row_number)) "
                + "LOCATION=@st63 AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 38 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type "
                + "'VARCHAR(16777216)', found 'NUMBER(18,0)'"},
            {"CREATE OR REPLACE EXTERNAL TABLE x31 (a INT NOT NULL AS (value:c1::varchar)) LOCATION=@st63 "
                + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 38 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type "
                + "'NUMBER(38,0)', found 'VARCHAR(134217728)'"},
            {"CREATE OR REPLACE EXTERNAL TABLE x32 (a INT AS (value:c1::varchar) COMMENT 'x') "
                + "LOCATION=@st63 AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 38 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type "
                + "'NUMBER(38,0)', found 'VARCHAR(134217728)'"},
            {"CREATE OR REPLACE EXTERNAL TABLE x33 (a INT AS (TO_DATE('2020-01-01'))) LOCATION=@st63 "
                + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 38 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type "
                + "'NUMBER(38,0)', found 'DATE'"},
            {"CREATE OR REPLACE EXTERNAL TABLE y1 (a INT AS (metadata$file_content_key)) LOCATION=@st63 "
                + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 37 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type "
                + "'NUMBER(38,0)', found 'VARCHAR(16777216)'"},
            {"CREATE OR REPLACE EXTERNAL TABLE y2 (a INT AS (metadata$file_last_modified)) LOCATION=@st63 "
                + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 37 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type "
                + "'NUMBER(38,0)', found 'TIMESTAMP_NTZ(3)'"},
            {"CREATE OR REPLACE EXTERNAL TABLE y3 (a INT AS (metadata$start_scan_time)) LOCATION=@st63 "
                + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 37 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type "
                + "'NUMBER(38,0)', found 'TIMESTAMP_LTZ(9)'"},
            {"CREATE OR REPLACE EXTERNAL TABLE y4 (a INT AS (value:c1::int + zz)) LOCATION=@st63 "
                + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 63 | invalid identifier 'ZZ'"},
            {"CREATE OR REPLACE EXTERNAL TABLE y5 (a VARCHAR AS (value:c1::varchar), b INT AS (a)) "
                + "LOCATION=@st63 AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 71 | Data type of virtual column "
                + "does not match the data type of its expression for column 'B'. Expected data type "
                + "'NUMBER(38,0)', found 'VARCHAR(134217728)'"},
            {"CREATE OR REPLACE EXTERNAL TABLE e1 (a INT AS (value:c1::varchar)) LOCATION=@st63 "
                + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 37 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type "
                + "'NUMBER(38,0)', found 'VARCHAR(134217728)'"},
            {"CREATE OR REPLACE EXTERNAL TABLE e7 (a VARCHAR AS (value:c1)) LOCATION=@st63 "
                + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 37 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type "
                + "'VARCHAR(16777216)', found 'VARIANT'"},
            {"CREATE OR REPLACE EXTERNAL TABLE e8 (a DATE AS (value:c1::varchar),\n b INT AS "
                + "(value:c2::int)) LOCATION=@st63 AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE "
                + "FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 37 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type 'DATE', "
                + "found 'VARCHAR(134217728)'"},
            {"CREATE OR REPLACE EXTERNAL TABLE e9 (b INT AS (value:c2::int), a DATE AS "
                + "(value:c1::varchar)) LOCATION=@st63 AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE "
                + "FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 63 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type 'DATE', "
                + "found 'VARCHAR(134217728)'"},
            {"CREATE OR REPLACE EXTERNAL TABLE e14 (a FLOAT AS (value:c1::int)) LOCATION=@st63 "
                + "AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE FILE_FORMAT=(TYPE=CSV)",
                "REFUSED SQL compilation error: error line 1 at position 38 | Data type of virtual column "
                + "does not match the data type of its expression for column 'A'. Expected data type 'FLOAT', "
                + "found 'NUMBER(38,0)'"},
            {"EXECUTE IMMEDIATE $$BEGIN CREATE OR REPLACE EXTERNAL TABLE e15 (a INT AS "
                + "(value:c1::varchar)) LOCATION=@st63 AUTO_REFRESH=FALSE REFRESH_ON_CREATE=FALSE "
                + "FILE_FORMAT=(TYPE=CSV); RETURN 'ok'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || "
                + "SQLSTATE; END;$$",
                "1482 42601"},
        });
    }

    @Test
    public void textOperandConversions() {
        engine.execute("CREATE OR REPLACE TABLE u (s VARCHAR, c INT, f FLOAT, n NUMBER(10,2))");
        assertCells(new String[][] {
            {"SELECT (s / c)::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST((FIXED_TO_FIXED(TO_NUMBER(U.S, 18, 5) AS"
                + " NUMBER(24,11)[UNKNOWN])) / U.C AS DATE)] for parameter 'TO_DATE'"},
            {"SELECT (c / s)::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST(SCALED_ROUND_INT_DIVIDE(U.C, "
                + "TO_NUMBER(U.S, 18, 5)) AS DATE)] for parameter 'TO_DATE'"},
            {"SELECT (s * c)::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST((TO_NUMBER(U.S, 18, 5)) * U.C AS DATE)] "
                + "for parameter 'TO_DATE'"},
            {"SELECT (s + c)::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST((TO_NUMBER(U.S, 38, 5)) + "
                + "(FIXED_TO_FIXED(U.C AS NUMBER(38,5)[UNKNOWN])) AS DATE)] for parameter 'TO_DATE'"},
            {"SELECT (s - c)::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST((TO_NUMBER(U.S, 38, 5)) - "
                + "(FIXED_TO_FIXED(U.C AS NUMBER(38,5)[UNKNOWN])) AS DATE)] for parameter 'TO_DATE'"},
            {"SELECT (s / n)::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST((FIXED_TO_FIXED(TO_NUMBER(U.S, 18, 5) AS"
                + " NUMBER(28,13)[UNKNOWN])) / U.N AS DATE)] for parameter 'TO_DATE'"},
            {"SELECT (s / f)::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST((TO_DOUBLE(U.S)) / U.F AS DATE)] for "
                + "parameter 'TO_DATE'"},
            {"SELECT (s / s)::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST((TO_DOUBLE(U.S)) / (TO_DOUBLE(U.S)) AS "
                + "DATE)] for parameter 'TO_DATE'"},
            {"SELECT (s % c)::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST((TO_NUMBER(U.S, 38, 5)) % "
                + "(FIXED_TO_FIXED(U.C AS NUMBER(38,5)[UNKNOWN])) AS DATE)] for parameter 'TO_DATE'"},
            {"SELECT (n / s)::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST((FIXED_TO_FIXED(U.N AS "
                + "NUMBER(26,13)[UNKNOWN])) / (TO_NUMBER(U.S, 18, 5)) AS DATE)] for parameter 'TO_DATE'"},
            {"SELECT (s * n)::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST((TO_NUMBER(U.S, 18, 5)) * U.N AS DATE)] "
                + "for parameter 'TO_DATE'"},
            {"SELECT (s / 2)::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST((FIXED_TO_FIXED(TO_NUMBER(U.S, 18, 5) AS"
                + " NUMBER(24,11)[UNKNOWN])) / 2 AS DATE)] for parameter 'TO_DATE'"},
            {"SELECT ('1' / c)::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST((FIXED_TO_FIXED(TO_NUMBER('1', 18, 0) AS"
                + " NUMBER(24,6)[UNKNOWN])) / U.C AS DATE)] for parameter 'TO_DATE'"},
            {"SELECT (s = c)::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST((TO_NUMBER(U.S, 38, 5)) = "
                + "(FIXED_TO_FIXED(U.C AS NUMBER(38,5)[UNKNOWN])) AS DATE)] for parameter 'TO_DATE'"},
            {"SELECT (s < n)::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST((TO_NUMBER(U.S, 18, 5)) < "
                + "(FIXED_TO_FIXED(U.N AS NUMBER(18,5)[UNKNOWN])) AS DATE)] for parameter 'TO_DATE'"},
            {"SELECT (s + 1.5)::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST((TO_NUMBER(U.S, 18, 5)) + "
                + "(FIXED_TO_FIXED(1.5 AS NUMBER(18,5)[UNKNOWN])) AS DATE)] for parameter 'TO_DATE'"},
            {"SELECT (s + '2')::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST((TO_DOUBLE(U.S)) + (TO_DOUBLE('2')) AS "
                + "DATE)] for parameter 'TO_DATE'"},
            {"SELECT ('5.5' + c)::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST((TO_NUMBER('5.5', 38, 1)) + "
                + "(FIXED_TO_FIXED(U.C AS NUMBER(38,1)[UNKNOWN])) AS DATE)] for parameter 'TO_DATE'"},
            {"SELECT (c * '2')::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST(U.C * (TO_NUMBER('2', 18, 0)) AS DATE)] "
                + "for parameter 'TO_DATE'"},
            {"SELECT (n / '2.50')::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST((FIXED_TO_FIXED(U.N AS "
                + "NUMBER(18,9)[UNKNOWN])) / (TO_NUMBER('2.50', 18, 1)) AS DATE)] for parameter 'TO_DATE'"},
            {"SELECT (s - f)::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST((TO_DOUBLE(U.S)) - U.F AS DATE)] for "
                + "parameter 'TO_DATE'"},
            {"SELECT NULLIF(s, c)::DATE FROM u",
                ""},
            {"SELECT ((s / c) + 1)::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST(((FIXED_TO_FIXED(TO_NUMBER(U.S, 18, 5) "
                + "AS NUMBER(24,11)[UNKNOWN])) / U.C) + (FIXED_TO_FIXED(1 AS NUMBER(24,11)[UNKNOWN])) AS DATE)]"
                + " for parameter 'TO_DATE'"},
            {"SELECT (s / c)::BOOLEAN FROM u",
                ""},
            {"SELECT (s / c)::BINARY FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST((FIXED_TO_FIXED(TO_NUMBER(U.S, 18, 5) AS"
                + " NUMBER(24,11)[UNKNOWN])) / U.C AS BINARY(67108864))] for parameter 'TO_BINARY'"},
            {"SELECT TO_DATE(s / c) FROM u",
                "REFUSED SQL compilation error: | invalid type [TO_DATE((FIXED_TO_FIXED(TO_NUMBER(U.S, 18, 5)"
                + " AS NUMBER(24,11)[UNKNOWN])) / U.C)] for parameter 'TO_DATE'"},
            {"SELECT (' 1 ' * n)::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST((TO_NUMBER(' 1 ', 18, 5)) * U.N AS "
                + "DATE)] for parameter 'TO_DATE'"},
            {"SELECT (s / 2.5)::DATE FROM u",
                "REFUSED SQL compilation error: | invalid type [CAST((FIXED_TO_FIXED(TO_NUMBER(U.S, 18, 5) AS"
                + " NUMBER(26,12)[UNKNOWN])) / 2.5 AS DATE)] for parameter 'TO_DATE'"},
        });
    }
}
