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
 * Live-verified cells: INFORMATION_SCHEMA.SEQUENCES, PIPES and FILE_FORMATS keep a quoted name's case;
 * FILE_FORMATS reads each option the format sets, else its type's default, and NULL for an option its type has
 * none of; TABLE_STORAGE_METRICS gives each table its own ID and a clone its source line's CLONE_GROUP_ID; a SQL
 * table function's quoted RETURNS TABLE columns keep their case; a view's column list that names more or fewer
 * columns than its body answers is refused at CREATE, a body selecting * included; and of a function's parameters
 * whose names fold alike, the last declared is the one its body reads.
 */
public class CatalogNameCaseAndRoutineSignaturesTest extends MeasuredCellsTestSupport {

    @Test
    public void quotedNamesAndFileFormatCells() {
        engine.execute("CREATE SEQUENCE \"lowseq\"");
        engine.execute("CREATE STAGE st");
        engine.execute("CREATE TABLE t1 (id INT)");
        engine.execute("CREATE PIPE \"lowpipe\" AS COPY INTO t1 FROM @st");
        engine.execute("CREATE FILE FORMAT \"lowff\" TYPE = CSV");
        engine.execute("CREATE FILE FORMAT ff TYPE = CSV");
        engine.execute("CREATE FILE FORMAT ffj TYPE = JSON");
        engine.execute("CREATE FILE FORMAT ffp TYPE = PARQUET");
        engine.execute("CREATE FILE FORMAT ffx TYPE = CSV FIELD_DELIMITER = '|' NULL_IF = ('a', 'b') COMPRESSION = "
            + "GZIP SKIP_HEADER = 1 DATE_FORMAT = 'YYYY'");
        assertCells(new String[][] {
            {"SELECT SEQUENCE_NAME FROM INFORMATION_SCHEMA.SEQUENCES",
                "lowseq"},
            {"SELECT PIPE_NAME FROM INFORMATION_SCHEMA.PIPES",
                "lowpipe"},
            {"SELECT FILE_FORMAT_NAME FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME ILIKE "
                + "'low%'",
                "lowff"},
            {"SELECT COUNT(*) FROM INFORMATION_SCHEMA.SEQUENCES WHERE SEQUENCE_NAME = 'lowseq'",
                "1"},
            {"SELECT FILE_FORMAT_TYPE FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FF'",
                "CSV"},
            {"SELECT RECORD_DELIMITER FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FF'",
                " | "},
            {"SELECT FIELD_DELIMITER FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FF'",
                ","},
            {"SELECT SKIP_HEADER FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FF'",
                "0"},
            {"SELECT DATE_FORMAT FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FF'",
                "AUTO"},
            {"SELECT TIME_FORMAT FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FF'",
                "AUTO"},
            {"SELECT TIMESTAMP_FORMAT FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FF'",
                "AUTO"},
            {"SELECT BINARY_FORMAT FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FF'",
                "HEX"},
            {"SELECT ESCAPE FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FF'",
                "NONE"},
            {"SELECT ESCAPE_UNENCLOSED_FIELD FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME ="
                + " 'FF'",
                "\\"},
            {"SELECT TRIM_SPACE FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FF'",
                "false"},
            {"SELECT FIELD_OPTIONALLY_ENCLOSED_BY FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE "
                + "FILE_FORMAT_NAME = 'FF'",
                "NONE"},
            {"SELECT NULL_IF FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FF'",
                "[\"\\\\N\"]"},
            {"SELECT COMPRESSION FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FF'",
                "AUTO"},
            {"SELECT ERROR_ON_COLUMN_COUNT_MISMATCH FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE "
                + "FILE_FORMAT_NAME = 'FF'",
                "true"},
            {"SELECT RECORD_DELIMITER FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFJ'",
                "null"},
            {"SELECT FIELD_DELIMITER FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFJ'",
                "null"},
            {"SELECT SKIP_HEADER FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFJ'",
                "null"},
            {"SELECT DATE_FORMAT FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFJ'",
                "AUTO"},
            {"SELECT TIME_FORMAT FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFJ'",
                "AUTO"},
            {"SELECT TIMESTAMP_FORMAT FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFJ'",
                "AUTO"},
            {"SELECT BINARY_FORMAT FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFJ'",
                "HEX"},
            {"SELECT ESCAPE FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFJ'",
                "null"},
            {"SELECT ESCAPE_UNENCLOSED_FIELD FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME ="
                + " 'FFJ'",
                "null"},
            {"SELECT TRIM_SPACE FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFJ'",
                "false"},
            {"SELECT FIELD_OPTIONALLY_ENCLOSED_BY FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE "
                + "FILE_FORMAT_NAME = 'FFJ'",
                "null"},
            {"SELECT NULL_IF FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFJ'",
                "[]"},
            {"SELECT COMPRESSION FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFJ'",
                "AUTO"},
            {"SELECT ERROR_ON_COLUMN_COUNT_MISMATCH FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE "
                + "FILE_FORMAT_NAME = 'FFJ'",
                "null"},
            {"SELECT RECORD_DELIMITER FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFP'",
                "null"},
            {"SELECT FIELD_DELIMITER FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFP'",
                "null"},
            {"SELECT DATE_FORMAT FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFP'",
                "null"},
            {"SELECT NULL_IF FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFP'",
                "[]"},
            {"SELECT COMPRESSION FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFP'",
                "AUTO"},
            {"SELECT TRIM_SPACE FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFP'",
                "false"},
            {"SELECT BINARY_FORMAT FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFP'",
                "null"},
            {"SELECT ESCAPE FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFP'",
                "null"},
            {"SELECT FIELD_DELIMITER FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFX'",
                "|"},
            {"SELECT NULL_IF FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFX'",
                "[\"a\",\"b\"]"},
            {"SELECT COMPRESSION FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFX'",
                "GZIP"},
            {"SELECT SKIP_HEADER FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFX'",
                "1"},
            {"SELECT DATE_FORMAT FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFX'",
                "YYYY"},
            {"SELECT RECORD_DELIMITER FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_NAME = 'FFX'",
                " | "},
        });
    }

    @Test
    public void tableIdsAndCloneGroups() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("CREATE TABLE ta (id INT)");
        engine.execute("CREATE TABLE tac CLONE ta");
        engine.execute("CREATE TABLE tacc CLONE tac");
        engine.execute("CREATE TABLE tq LIKE ta");
        engine.execute("CREATE TABLE tas AS SELECT * FROM ta");
        engine.execute("CREATE TRANSIENT TABLE tt (id INT)");
        engine.execute("CREATE TRANSIENT TABLE ttc CLONE tt");
        assertCells(new String[][] {
            {"SELECT ID IS NULL FROM INFORMATION_SCHEMA.TABLE_STORAGE_METRICS WHERE TABLE_CATALOG = "
                + "CURRENT_DATABASE() AND CATALOG_DROPPED IS NULL AND SCHEMA_DROPPED IS NULL AND TABLE_DROPPED "
                + "IS NULL AND TABLE_SCHEMA = CURRENT_SCHEMA() AND TABLE_NAME = 'TA'",
                "false"},
            {"SELECT CLONE_GROUP_ID = ID FROM INFORMATION_SCHEMA.TABLE_STORAGE_METRICS WHERE TABLE_CATALOG"
                + " = CURRENT_DATABASE() AND CATALOG_DROPPED IS NULL AND SCHEMA_DROPPED IS NULL AND "
                + "TABLE_DROPPED IS NULL AND TABLE_SCHEMA = CURRENT_SCHEMA() AND TABLE_NAME = 'TA'",
                "true"},
            {"SELECT c.CLONE_GROUP_ID = a.ID FROM INFORMATION_SCHEMA.TABLE_STORAGE_METRICS a, "
                + "INFORMATION_SCHEMA.TABLE_STORAGE_METRICS c WHERE a.TABLE_CATALOG = CURRENT_DATABASE() AND "
                + "c.TABLE_CATALOG = CURRENT_DATABASE() AND a.CATALOG_DROPPED IS NULL AND c.CATALOG_DROPPED IS "
                + "NULL AND a.SCHEMA_DROPPED IS NULL AND c.SCHEMA_DROPPED IS NULL AND a.TABLE_DROPPED IS NULL "
                + "AND c.TABLE_DROPPED IS NULL AND a.TABLE_SCHEMA = CURRENT_SCHEMA() AND c.TABLE_SCHEMA = "
                + "CURRENT_SCHEMA() AND a.TABLE_NAME = 'TA' AND c.TABLE_NAME = 'TAC'",
                "true"},
            {"SELECT c.ID = a.ID FROM INFORMATION_SCHEMA.TABLE_STORAGE_METRICS a, "
                + "INFORMATION_SCHEMA.TABLE_STORAGE_METRICS c WHERE a.TABLE_CATALOG = CURRENT_DATABASE() AND "
                + "c.TABLE_CATALOG = CURRENT_DATABASE() AND a.CATALOG_DROPPED IS NULL AND c.CATALOG_DROPPED IS "
                + "NULL AND a.SCHEMA_DROPPED IS NULL AND c.SCHEMA_DROPPED IS NULL AND a.TABLE_DROPPED IS NULL "
                + "AND c.TABLE_DROPPED IS NULL AND a.TABLE_SCHEMA = CURRENT_SCHEMA() AND c.TABLE_SCHEMA = "
                + "CURRENT_SCHEMA() AND a.TABLE_NAME = 'TA' AND c.TABLE_NAME = 'TAC'",
                "false"},
            {"SELECT c.CLONE_GROUP_ID = a.ID FROM INFORMATION_SCHEMA.TABLE_STORAGE_METRICS a, "
                + "INFORMATION_SCHEMA.TABLE_STORAGE_METRICS c WHERE a.TABLE_CATALOG = CURRENT_DATABASE() AND "
                + "c.TABLE_CATALOG = CURRENT_DATABASE() AND a.CATALOG_DROPPED IS NULL AND c.CATALOG_DROPPED IS "
                + "NULL AND a.SCHEMA_DROPPED IS NULL AND c.SCHEMA_DROPPED IS NULL AND a.TABLE_DROPPED IS NULL "
                + "AND c.TABLE_DROPPED IS NULL AND a.TABLE_SCHEMA = CURRENT_SCHEMA() AND c.TABLE_SCHEMA = "
                + "CURRENT_SCHEMA() AND a.TABLE_NAME = 'TA' AND c.TABLE_NAME = 'TACC'",
                "true"},
            {"SELECT c.CLONE_GROUP_ID = c.ID FROM INFORMATION_SCHEMA.TABLE_STORAGE_METRICS a, "
                + "INFORMATION_SCHEMA.TABLE_STORAGE_METRICS c WHERE a.TABLE_CATALOG = CURRENT_DATABASE() AND "
                + "c.TABLE_CATALOG = CURRENT_DATABASE() AND a.CATALOG_DROPPED IS NULL AND c.CATALOG_DROPPED IS "
                + "NULL AND a.SCHEMA_DROPPED IS NULL AND c.SCHEMA_DROPPED IS NULL AND a.TABLE_DROPPED IS NULL "
                + "AND c.TABLE_DROPPED IS NULL AND a.TABLE_SCHEMA = CURRENT_SCHEMA() AND c.TABLE_SCHEMA = "
                + "CURRENT_SCHEMA() AND a.TABLE_NAME = 'TA' AND c.TABLE_NAME = 'TQ'",
                "true"},
            {"SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_STORAGE_METRICS WHERE TABLE_CATALOG = "
                + "CURRENT_DATABASE() AND CATALOG_DROPPED IS NULL AND SCHEMA_DROPPED IS NULL AND TABLE_DROPPED "
                + "IS NULL AND TABLE_SCHEMA = CURRENT_SCHEMA() AND TABLE_NAME = 'TA'",
                "1"},
            {"SELECT TYPEOF(ID::VARIANT) FROM INFORMATION_SCHEMA.TABLE_STORAGE_METRICS WHERE TABLE_CATALOG"
                + " = CURRENT_DATABASE() AND CATALOG_DROPPED IS NULL AND SCHEMA_DROPPED IS NULL AND "
                + "TABLE_DROPPED IS NULL AND TABLE_SCHEMA = CURRENT_SCHEMA() AND TABLE_NAME = 'TA'",
                "INTEGER"},
            {"SELECT TYPEOF(CLONE_GROUP_ID::VARIANT) FROM INFORMATION_SCHEMA.TABLE_STORAGE_METRICS WHERE "
                + "TABLE_CATALOG = CURRENT_DATABASE() AND CATALOG_DROPPED IS NULL AND SCHEMA_DROPPED IS NULL "
                + "AND TABLE_DROPPED IS NULL AND TABLE_SCHEMA = CURRENT_SCHEMA() AND TABLE_NAME = 'TAC'",
                "INTEGER"},
            {"SELECT ID > 0 FROM INFORMATION_SCHEMA.TABLE_STORAGE_METRICS WHERE TABLE_CATALOG = "
                + "CURRENT_DATABASE() AND CATALOG_DROPPED IS NULL AND SCHEMA_DROPPED IS NULL AND TABLE_DROPPED "
                + "IS NULL AND TABLE_SCHEMA = CURRENT_SCHEMA() AND TABLE_NAME = 'TA'",
                "true"},
            {"SELECT CLONE_GROUP_ID = ID FROM INFORMATION_SCHEMA.TABLE_STORAGE_METRICS WHERE TABLE_CATALOG"
                + " = CURRENT_DATABASE() AND CATALOG_DROPPED IS NULL AND SCHEMA_DROPPED IS NULL AND "
                + "TABLE_DROPPED IS NULL AND TABLE_SCHEMA = CURRENT_SCHEMA() AND TABLE_NAME = 'TAS'",
                "true"},
            {"SELECT c.CLONE_GROUP_ID = a.ID FROM INFORMATION_SCHEMA.TABLE_STORAGE_METRICS a, "
                + "INFORMATION_SCHEMA.TABLE_STORAGE_METRICS c WHERE a.TABLE_CATALOG = CURRENT_DATABASE() AND "
                + "c.TABLE_CATALOG = CURRENT_DATABASE() AND a.CATALOG_DROPPED IS NULL AND c.CATALOG_DROPPED IS "
                + "NULL AND a.SCHEMA_DROPPED IS NULL AND c.SCHEMA_DROPPED IS NULL AND a.TABLE_DROPPED IS NULL "
                + "AND c.TABLE_DROPPED IS NULL AND a.TABLE_SCHEMA = CURRENT_SCHEMA() AND c.TABLE_SCHEMA = "
                + "CURRENT_SCHEMA() AND a.TABLE_NAME = 'TT' AND c.TABLE_NAME = 'TTC'",
                "true"},
            {"SELECT COUNT(DISTINCT ID) FROM INFORMATION_SCHEMA.TABLE_STORAGE_METRICS WHERE TABLE_CATALOG "
                + "= CURRENT_DATABASE() AND CATALOG_DROPPED IS NULL AND SCHEMA_DROPPED IS NULL AND "
                + "TABLE_DROPPED IS NULL AND TABLE_SCHEMA = CURRENT_SCHEMA()",
                "7"},
            {"SELECT COUNT(DISTINCT CLONE_GROUP_ID) FROM INFORMATION_SCHEMA.TABLE_STORAGE_METRICS WHERE "
                + "TABLE_CATALOG = CURRENT_DATABASE() AND CATALOG_DROPPED IS NULL AND SCHEMA_DROPPED IS NULL "
                + "AND TABLE_DROPPED IS NULL AND TABLE_SCHEMA = CURRENT_SCHEMA()",
                "4"},
        });
    }

    @Test
    public void viewColumnListCounts() {
        engine.execute("CREATE TABLE vw (a INT, b INT, c INT)");
        engine.execute("CREATE VIEW vs4 (p, q, r) AS SELECT * FROM vw");
        assertCells(new String[][] {
            {"CREATE VIEW vs (p, q) AS SELECT * FROM vw",
                "REFUSED SQL compilation error: | Invalid column definition list"},
            {"CREATE VIEW vs2 (p, q, r, s) AS SELECT * FROM vw",
                "REFUSED SQL compilation error: | Invalid column definition list"},
            {"CREATE VIEW vs3 (p, q) AS SELECT * FROM vw, vw AS w2",
                "REFUSED SQL compilation error: | Invalid column definition list"},
            {"SELECT p FROM vs4",
                ""},
            {"CREATE VIEW vs6 (p, q) AS SELECT *, 1 FROM vw",
                "REFUSED SQL compilation error: | Invalid column definition list"},
            {"CREATE VIEW vs7 (p, q) AS SELECT vw.* FROM vw",
                "REFUSED SQL compilation error: | Invalid column definition list"},
            {"CREATE VIEW vs9 (p) AS SELECT * EXCLUDE c FROM vw",
                "REFUSED SQL compilation error: | Invalid column definition list"},
            {"CREATE VIEW vs10 (p, q) AS SELECT * FROM vw UNION ALL SELECT 1, 2, 3",
                "REFUSED SQL compilation error: | Invalid column definition list"},
            {"CREATE VIEW vs11 (p, q) AS SELECT * FROM (SELECT 1 x, 2 y, 3 z)",
                "REFUSED SQL compilation error: | Invalid column definition list"},
            {"CREATE OR REPLACE VIEW vs4 (p, q) AS SELECT * FROM vw",
                "REFUSED SQL compilation error: | Invalid column definition list"},
            {"SELECT p FROM vs4",
                ""},
            {"CREATE VIEW vs13 (p, q) AS SELECT * FROM vw WHERE nope = 1",
                "REFUSED SQL compilation error: error line 1 at position 50 | invalid identifier 'NOPE'"},
            {"CREATE VIEW vs14 (p, q) AS SELECT * ILIKE '%a%' FROM vw",
                "REFUSED SQL compilation error: | Invalid column definition list"},
            {"CREATE VIEW vs15 (p, q) AS SELECT * RENAME a AS z FROM vw",
                "REFUSED SQL compilation error: | Invalid column definition list"},
            {"CREATE VIEW vs16 (p, q) AS WITH c AS (SELECT 1 a) SELECT * FROM vw",
                "REFUSED SQL compilation error: | Invalid column definition list"},
            {"CREATE VIEW vs17 (p, q) AS SELECT * FROM TABLE(GENERATOR(ROWCOUNT => 1))",
                "REFUSED SQL compilation error: error line 1 at position 27 | SELECT with no columns"},
            {"CREATE VIEW vs18 (p) AS SELECT * FROM TABLE(GENERATOR(ROWCOUNT => 1))",
                "REFUSED SQL compilation error: error line 1 at position 24 | SELECT with no columns"},
            {"CREATE VIEW vs19 (p, q) AS SELECT * FROM vw, LATERAL FLATTEN(INPUT => [1])",
                "REFUSED SQL compilation error: | Invalid column definition list"},
            {"CREATE VIEW vs20 (p, q) AS SELECT *, a FROM vw",
                "REFUSED SQL compilation error: | Invalid column definition list"},
        });
    }

    @Test
    public void tableFunctionColumnCase() {
        engine.execute("CREATE OR REPLACE FUNCTION tq() RETURNS TABLE (\"Col A\" INT, \"low\" VARCHAR) AS 'select 1,"
            + " ''x'''");
        engine.execute("CREATE TABLE ct AS SELECT * FROM TABLE(tq())");
        engine.execute("CREATE VIEW tqv AS SELECT * FROM TABLE(tq())");
        engine.execute("CREATE OR REPLACE FUNCTION tq2() RETURNS TABLE (\"Col A\" INT, mixed VARCHAR) AS 'select 1 "
            + "as \"Col A\", ''x'''");
        assertCells(new String[][] {
            {"SELECT \"Col A\" FROM TABLE(tq())",
                "1"},
            {"SELECT t.\"low\" FROM TABLE(tq()) t",
                "x"},
            {"SELECT \"COL A\" FROM TABLE(tq())",
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"COL A\"'"},
            {"SELECT low FROM TABLE(tq())",
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'LOW'"},
            {"SELECT LISTAGG(COLUMN_NAME, '|') WITHIN GROUP (ORDER BY ORDINAL_POSITION) FROM "
                + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'CT'",
                "Col A|low"},
            {"SELECT \"low\" FROM tqv",
                "x"},
            {"SELECT \"Col A\" || mixed FROM TABLE(tq2())",
                "1x"},
            {"SELECT \"Col A\" FROM TABLE(tq2()) WHERE \"MIXED\" = 'x'",
                "1"},
            {"SELECT f.\"low\" FROM TABLE(tq()) f, LATERAL (SELECT 1) l",
                "x"},
        });
    }

    @Test
    public void parametersFoldingAlike() {
        engine.execute("CREATE OR REPLACE FUNCTION qq(\"a\" INT, \"A\" INT) RETURNS INT AS 'a*10 + A'");
        engine.execute("CREATE OR REPLACE FUNCTION qq2(\"a\" INT, a INT) RETURNS INT AS 'a'");
        engine.execute("CREATE OR REPLACE FUNCTION qq4(\"a\" INT, \"b\" INT) RETURNS INT AS 'a + b'");
        engine.execute("CREATE OR REPLACE FUNCTION qq5(\"a\" INT, \"A\" INT) RETURNS TABLE (x INT) AS 'select a'");
        engine.execute("CREATE OR REPLACE PROCEDURE pp1(\"a\" INT, \"A\" INT) RETURNS INT LANGUAGE SQL AS 'BEGIN "
            + "RETURN 1; END'");
        engine.execute("CREATE OR REPLACE FUNCTION qq8(\"a b\" INT, \"A B\" INT) RETURNS INT AS '1'");
        engine.execute("CREATE OR REPLACE FUNCTION qq9(\"Mixed\" INT) RETURNS INT AS 'mixed'");
        engine.execute("CREATE OR REPLACE FUNCTION qa(\"a\" INT, \"A\" INT) RETURNS INT AS 'a'");
        engine.execute("CREATE OR REPLACE FUNCTION qb(\"A\" INT, \"a\" INT) RETURNS INT AS 'a'");
        engine.execute("CREATE OR REPLACE FUNCTION qc(\"a\" INT, a INT) RETURNS INT AS 'a'");
        engine.execute("CREATE OR REPLACE FUNCTION qd(\"a\" INT, \"A\" INT, \"b\" INT) RETURNS INT AS 'a * 100 + b'");
        engine.execute("CREATE OR REPLACE FUNCTION qe(\"a\" INT, \"A\" INT) RETURNS INT AS '\"A\"'");
        engine.execute("CREATE OR REPLACE FUNCTION qf(\"a\" INT, \"A\" INT) RETURNS TABLE (x INT) AS 'select a'");
        engine.execute("CREATE OR REPLACE FUNCTION qg(\"a\" INT, \"A\" INT) RETURNS INT AS 'select a'");
        engine.execute("CREATE OR REPLACE FUNCTION qh(\"a\" VARCHAR, \"A\" INT) RETURNS VARCHAR AS 'a::VARCHAR'");
        engine.execute("CREATE OR REPLACE FUNCTION qi(\"a\" INT, \"A\" INT) RETURNS INT AS 'a'");
        engine.execute("CREATE OR REPLACE FUNCTION qj(\"a\" INT, \"A\" INT) RETURNS INT AS 'a + 0' ");
        engine.execute("CREATE OR REPLACE PROCEDURE pa(\"a\" INT, \"A\" INT) RETURNS INT LANGUAGE SQL AS 'BEGIN "
            + "RETURN a; END'");
        assertCells(new String[][] {
            {"SELECT qq(1, 2)",
                "22"},
            {"CREATE OR REPLACE FUNCTION qq3(a INT, A INT) RETURNS INT AS 'a'",
                "REFUSED Argument 'A' repeats in the function signature."},
            {"SELECT qq4(1, 2)",
                "3"},
            {"CREATE OR REPLACE FUNCTION qq7(a INT, a INT) RETURNS INT AS 'a'",
                "REFUSED Argument 'A' repeats in the function signature."},
            {"SELECT qq9(\"Mixed\" => 1)",
                "1"},
            {"SELECT qq9(mixed => 1)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [MIXED] do not "
                + "match any signature for function QQ9"},
            {"SELECT qq9(MIXED => 1)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [MIXED] do not "
                + "match any signature for function QQ9"},
            {"SELECT qa(1, 2)",
                "2"},
            {"SELECT qb(1, 2)",
                "2"},
            {"SELECT qc(1, 2)",
                "2"},
            {"SELECT qd(1, 2, 3)",
                "203"},
            {"SELECT qe(1, 2)",
                "2"},
            {"SELECT x FROM TABLE(qf(1, 2))",
                "2"},
            {"SELECT qg(1, 2)",
                "2"},
            {"SELECT qh('x', 2)",
                "2"},
            {"SELECT qi(\"a\" => 1, \"A\" => 2)",
                "2"},
            {"SELECT qi(\"A\" => 2, \"a\" => 1)",
                "2"},
            {"SELECT qi(1, NULL)",
                "null"},
            {"SELECT qj(5, 7)",
                "7"},
            {"CALL pa(1, 2)",
                "2"},
        });
    }
}
