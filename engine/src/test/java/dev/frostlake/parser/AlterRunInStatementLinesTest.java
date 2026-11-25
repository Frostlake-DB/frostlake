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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A statement run into a complete ALTER without its semicolon, where live gives the ALTER's statement up further
 * back than the word run in: a file format's or a stream's names its kind and the token after it, a policy's or a
 * user's list its first word, a schema's, a database's or a view's property list its first name, and a RENAME its
 * RENAME or TO, whatever statement the word opens; the statement run in is read past to its end (live-verified).
 */
public class AlterRunInStatementLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void statementsRunIntoAnAlter() {
        assertCells(new String[][] {
            {"ALTER FILE FORMAT ff1 SET COMMENT = 'y' SET SKIP_HEADER = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff1 SET COMMENT = 'y' UNSET COMMENT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff1 SET COMMENT = 'y' x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff1 SET COMMENT = 'y',",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff1 SET COMMENT = 'y' RENAME TO x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT IF EXISTS ff1 SET COMMENT = 'y' SET SKIP_HEADER = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff1 SET COMMENT = 'y' SET",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff1 SET COMMENT = 'y' SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff1 SET COMMENT = 'y' ALTER TABLE t SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff1 RENAME TO x SET COMMENT = 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff1 UNSET COMMENT SET COMMENT = 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff1 SET SKIP_HEADER = 1 SET COMMENT = 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff1 SET COMMENT = 'y' SET x = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff1 SET COMMENT = 'y' SET a b c",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff1 SET COMMENT = 'y';\nSET x = 1 SET y = 2",
                "REFUSED SQL compilation error: | syntax error line 2 at position 10 unexpected 'SET'."},
            {"ALTER FILE FORMAT ff1 SET COMMENT = 'y' SET COMMENT = 'z';",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff1 SET COMMENT = 'y'\nSET SKIP_HEADER = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff1 SET TAG t1 = 'a' SET COMMENT = 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff1 SET COMMENT = 'y' SET TAG t1 = 'a'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER STREAM st SET COMMENT = 'y' SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'STREAM'. | "
                + "syntax error line 1 at position 13 unexpected 'st'."},
            {"ALTER STREAM st SET COMMENT = 'y' UNSET COMMENT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'STREAM'. | "
                + "syntax error line 1 at position 13 unexpected 'st'."},
            {"ALTER TABLE t SET COMMENT = 'y' SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SET'."},
            {"ALTER TABLE t SET DATA_RETENTION_TIME_IN_DAYS = 1 SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'SET'."},
            {"ALTER VIEW v SET COMMENT = 'y' SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'COMMENT'."},
            {"ALTER SCHEMA s SET COMMENT = 'y' SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'COMMENT'. | "
                + "syntax error line 1 at position 29 unexpected ''y''."},
            {"ALTER DATABASE PRB_EAX SET COMMENT = 'y' SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'COMMENT'."},
            {"ALTER SEQUENCE s SET COMMENT = 'y' SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'SET'."},
            {"ALTER SEQUENCE s SET INCREMENT = 2 SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'SET'."},
            {"ALTER STAGE s SET COMMENT = 'y' SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SET'."},
            {"ALTER TAG g SET COMMENT = 'y' SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'SET'."},
            {"ALTER MASKING POLICY mp SET COMMENT = 'y' SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'COMMENT'."},
            {"ALTER ROW ACCESS POLICY rp SET COMMENT = 'y' SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'COMMENT'."},
            {"ALTER TASK k SET COMMENT = 'y' SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SET'."},
            {"ALTER PIPE p SET COMMENT = 'y' SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SET'."},
            {"ALTER FUNCTION f(INT) SET COMMENT = 'y' SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'SET'."},
            {"ALTER PROCEDURE p(INT) SET COMMENT = 'y' SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'SET'."},
            {"ALTER ALERT a SET COMMENT = 'y' SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SET'."},
            {"ALTER SESSION SET QUERY_TAG = 'y' SET QUERY_TAG = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'SET'."},
            {"ALTER SECRET sr SET COMMENT = 'y' SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'SET'."},
            {"ALTER EVENT TABLE e SET COMMENT = 'y' SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'TABLE'."},
            {"ALTER ICEBERG TABLE i SET COMMENT = 'y' SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'SET'."},
            {"ALTER FILE FORMAT ff SET COMMENT = 'y' SET x = 1; SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'. | syntax error line 1 at position 61"
                + " unexpected 'y'."},
            {"ALTER FILE FORMAT ff SET COMMENT = 'y' x; SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'. | syntax error line 1 at position 53"
                + " unexpected 'y'."},
            {"SELECT 1; ALTER FILE FORMAT ff SET COMMENT = 'y' SET x = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'FILE'. | "
                + "syntax error line 1 at position 21 unexpected 'FORMAT'."},
            {"ALTER STREAM st SET COMMENT = 'y' SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'STREAM'. | "
                + "syntax error line 1 at position 13 unexpected 'st'."},
            {"ALTER STREAM IF EXISTS st SET COMMENT = 'y' SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'STREAM'. | "
                + "syntax error line 1 at position 13 unexpected 'IF'."},
            {"ALTER FILE FORMAT \"ff\" SET COMMENT = 'y' SET x = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT PRB_EAX.PUBLIC.ff SET COMMENT = 'y' SET x = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER STREAM st SET COMMENT = 'y'\nSELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'STREAM'. | "
                + "syntax error line 1 at position 13 unexpected 'st'."},
            {"ALTER FILE FORMAT ff SET COMMENT = 'y' BEGIN SELECT 1; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff SET COMMENT = 'y' (SELECT 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff SET COMMENT = 'y' SET x = 1 SET y = 2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER STREAM st SET TAG t1 = 'a' SET COMMENT = 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'STREAM'. | "
                + "syntax error line 1 at position 13 unexpected 'st'."},
            {"SELECT 1 SELECT 2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'SELECT'."},
            {"ALTER FILE FORMAT ff RENAME TO x RENAME TO y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER STREAM st UNSET COMMENT UNSET COMMENT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'STREAM'. | "
                + "syntax error line 1 at position 13 unexpected 'st'."},
            {"ALTER FILE FORMAT ff SET COMMENT = 'y' SHOW TABLES",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff SET COMMENT = 'y' CREATE TABLE t (a INT)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff SET COMMENT = 'y' SET x = 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"SELECT 1; ALTER STREAM st SET COMMENT = 'y' SET x = 1; SELECT 2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'STREAM'. | "
                + "syntax error line 1 at position 23 unexpected 'st'."},
            {"ALTER FILE FORMAT ff SET COMMENT = 'y' DROP TABLE t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER VIEW v SET COMMENT = 'y' x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'COMMENT'."},
            {"ALTER VIEW v SET SECURE SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'SET'."},
            {"ALTER VIEW v UNSET COMMENT SET COMMENT = 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SET'."},
            {"ALTER VIEW v SET COMMENT = 'y' SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'COMMENT'."},
            {"ALTER VIEW v SET COMMENT = 'y' UNSET COMMENT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'COMMENT'."},
            {"ALTER SCHEMA s SET COMMENT = 'y' x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'COMMENT'. | "
                + "syntax error line 1 at position 29 unexpected ''y''."},
            {"ALTER SCHEMA s SET DATA_RETENTION_TIME_IN_DAYS = 1 SET COMMENT = 'z'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected "
                + "'DATA_RETENTION_TIME_IN_DAYS'. | syntax error line 1 at position 49 unexpected '1'."},
            {"ALTER SCHEMA s SET COMMENT = 'y' SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'COMMENT'. | "
                + "syntax error line 1 at position 29 unexpected ''y''."},
            {"ALTER DATABASE PRB_EAX SET COMMENT = 'y' x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'COMMENT'."},
            {"ALTER DATABASE PRB_EAX SET COMMENT = 'y' SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'COMMENT'."},
            {"ALTER MASKING POLICY mp SET COMMENT = 'y' SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'COMMENT'."},
            {"ALTER TABLE t SET COMMENT = 'y' SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'."},
            {"ALTER TABLE t UNSET COMMENT SET COMMENT = 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SET'."},
            {"ALTER TABLE t RENAME TO x SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'RENAME'."},
            {"ALTER SEQUENCE s RENAME TO x SET y = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'TO'. | "
                + "syntax error line 1 at position 27 unexpected 'x'."},
            {"ALTER VIEW v RENAME TO x SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'RENAME'."},
            {"ALTER DYNAMIC TABLE d RENAME TO x SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'TO'."},
            {"ALTER STAGE s RENAME TO x SET y = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'RENAME'."},
            {"ALTER MASKING POLICY mp SET BODY -> 'x' SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'SELECT'."},
            {"ALTER MASKING POLICY mp UNSET COMMENT SET COMMENT = 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'UNSET'."},
            {"ALTER MASKING POLICY mp SET TAG t1 = 'a' SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'TAG'."},
            {"ALTER ROW ACCESS POLICY rp SET COMMENT = 'y' SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'COMMENT'."},
            {"ALTER VIEW v SET TAG t1 = 'a' SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'SELECT'."},
            {"ALTER VIEW v UNSET COMMENT SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"ALTER SCHEMA s UNSET COMMENT SET COMMENT = 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'COMMENT'."},
            {"ALTER SCHEMA s SET COMMENT = 'y', DATA_RETENTION_TIME_IN_DAYS = 1 SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'COMMENT'. | "
                + "syntax error line 1 at position 29 unexpected ''y''. | syntax error line 1 at position 64 "
                + "unexpected '1'."},
            {"ALTER DATABASE PRB_EAX UNSET COMMENT SET COMMENT = 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'COMMENT'."},
            {"ALTER DATABASE PRB_EAX SET DATA_RETENTION_TIME_IN_DAYS = 1 SET COMMENT = 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected "
                + "'DATA_RETENTION_TIME_IN_DAYS'."},
            {"/* c */ ALTER FILE FORMAT ff SET COMMENT = 'y' SET x = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff\nSET COMMENT = 'y'\nSET x = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER STREAM st SET COMMENT = 'y' SET x = 1; SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'STREAM'. | "
                + "syntax error line 1 at position 13 unexpected 'st'. | syntax error line 1 at position 56 "
                + "unexpected 'y'."},
            {"ALTER STREAM st UNSET TAG t1 SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'STREAM'. | "
                + "syntax error line 1 at position 13 unexpected 'st'."},
            {"ALTER FILE FORMAT ff SET COMMENT = 'y' SET x = 1;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff SET COMMENT = 'y' SET x = 1 SELECT 2; SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'. | syntax error line 1 at position 70"
                + " unexpected 'y'."},
            {"ALTER FILE FORMAT ff SET TYPE = CSV FIELD_DELIMITER = ',' SET x = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'."},
            {"ALTER FILE FORMAT ff SET COMMENT = 'y' SET x = 1 ; ALTER FILE FORMAT ff SET COMMENT = 'y' "
                + "SET x = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'FILE'. | "
                + "syntax error line 1 at position 11 unexpected 'FORMAT'. | syntax error line 1 at position 57"
                + " unexpected 'FILE'. | syntax error line 1 at position 62 unexpected 'FORMAT'."},
        });
    }
}
