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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The lines of a syntax error among a CREATE TABLE's options after its column list, of a table's ON COMMIT clause, of
 * a START or a FOR after a query or as a select item's alias, and of an ON or a START in a view's header
 * (live-verified): live reads on past the refused token as further options (a name, its '=' and its value) and
 * refuses again wherever that reading fails; ON opens ON COMMIT, which only a temporary table takes; START opens START
 * WITH and FOR opens FOR UPDATE, never a statement of their own.
 */
public class TableTailLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void tableTailAndClauseWords() {
        engine.execute("CREATE OR REPLACE TABLE pt_t (i INT)");
        engine.execute("CREATE OR REPLACE TABLE lk (i INT)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE tq (a INT) CHANGE_TRACKING = ON",
                "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'ON'. | "
                + "syntax error line 1 at position 55 unexpected '<EOF>'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CHANGE_TRACKING = ON x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'ON'. | "
                + "syntax error line 1 at position 56 unexpected 'x'. | syntax error line 1 at position 57 "
                + "unexpected '<EOF>'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CHANGE_TRACKING = ON;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'ON'. | "
                + "syntax error line 1 at position 55 unexpected ';'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CHANGE_TRACKING = ON = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'ON'. | "
                + "syntax error line 1 at position 56 unexpected '='."},
            {"CREATE OR REPLACE TABLE tq (a INT) CHANGE_TRACKING = ON x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'ON'. | "
                + "syntax error line 1 at position 56 unexpected 'x'. | syntax error line 1 at position 58 "
                + "unexpected 'y'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CHANGE_TRACKING = ON COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'ON'. | "
                + "syntax error line 1 at position 56 unexpected 'COMMENT'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CHANGE_TRACKING = OFF",
                "REFUSED SQL compilation error: | invalid value [OFF] for parameter 'CHANGE_TRACKING'"},
            {"CREATE OR REPLACE TABLE tq (a INT) CHANGE_TRACKING = SET",
                "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'SET'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CHANGE_TRACKING = SET x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'SET'. | "
                + "syntax error line 1 at position 58 unexpected '<EOF>'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CHANGE_TRACKING = WITH",
                "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'WITH'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CHANGE_TRACKING = SELECT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'SELECT'."},
            {"CREATE OR REPLACE TABLE tq (a INT) COMMENT = ON",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'ON'. | "
                + "syntax error line 1 at position 47 unexpected '<EOF>'."},
            {"CREATE OR REPLACE TABLE tq (a INT) DATA_RETENTION_TIME_IN_DAYS = ON",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'ON'. | "
                + "syntax error line 1 at position 67 unexpected '<EOF>'."},
            {"ALTER TABLE pt_t SET CHANGE_TRACKING = ON",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'ON'."},
            {"CREATE OR REPLACE VIEW vq CHANGE_TRACKING = ON AS SELECT 1 a",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'ON'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'x'. | syntax"
                + " error line 1 at position 44 unexpected '<EOF>'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'x'. | syntax"
                + " error line 1 at position 45 unexpected 'y'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER x y z",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'x'. | syntax"
                + " error line 1 at position 45 unexpected 'y'. | syntax error line 1 at position 48 unexpected"
                + " '<EOF>'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER x;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'x'. | syntax"
                + " error line 1 at position 44 unexpected ';'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER",
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '<EOF>'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER = 1",
                "REFUSED SQL compilation error: | invalid property 'CLUSTER' for 'TABLE'"},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER BY",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '<EOF>'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER x = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'x'."},
            {"CREATE OR REPLACE TABLE tq (a INT) COPY x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'x'. | syntax"
                + " error line 1 at position 41 unexpected '<EOF>'."},
            {"CREATE OR REPLACE TABLE tq (a INT) COPY x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'x'. | syntax"
                + " error line 1 at position 42 unexpected 'y'."},
            {"CREATE OR REPLACE TABLE tq (a INT) COPY",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '<EOF>'."},
            {"CREATE OR REPLACE TABLE tq (a INT) COPY = 1",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'COPY'"},
            {"CREATE OR REPLACE TABLE tq (a INT) COPY GRANTS x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected '<EOF>'."},
            {"CREATE OR REPLACE TABLE tq (a INT) WITH = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '='."},
            {"CREATE OR REPLACE TABLE tq (a INT) CALL p()",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'p'. | syntax"
                + " error line 1 at position 41 unexpected '('."},
            {"CREATE OR REPLACE TABLE tq (a INT) CALL p(1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'p'. | syntax"
                + " error line 1 at position 41 unexpected '('."},
            {"CREATE OR REPLACE TABLE tq (a INT) CALL = 1",
                "REFUSED SQL compilation error: | invalid property 'CALL' for 'TABLE'"},
            {"CREATE OR REPLACE TABLE tq (a INT) ON = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '='."},
            {"CREATE OR REPLACE TABLE tq (a INT) ON",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '<EOF>'."},
            {"CREATE OR REPLACE TABLE tq (a INT) ON x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'x'. | syntax"
                + " error line 1 at position 39 unexpected '<EOF>'."},
            {"CREATE OR REPLACE TABLE tq (a INT) ON x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'x'. | syntax"
                + " error line 1 at position 40 unexpected 'y'."},
            {"CREATE OR REPLACE TABLE tq (a INT) ON;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ';'."},
            {"CREATE OR REPLACE TABLE tq (a INT) ON COMMIT PRESERVE ROWS",
                "REFUSED SQL compilation error: | 'ON COMMIT' can only be used with temporary object"},
            {"CREATE OR REPLACE TABLE tq (a INT) ON COMMIT DELETE ROWS",
                "REFUSED SQL compilation error: | 'ON COMMIT' can only be used with temporary object"},
            {"CREATE OR REPLACE TABLE tq (a INT) ON COMMIT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '<EOF>'."},
            {"CREATE OR REPLACE TABLE tq (a INT) ON COMMIT x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'x'. | syntax"
                + " error line 1 at position 46 unexpected '<EOF>'."},
            {"CREATE OR REPLACE TEMPORARY TABLE tq (a INT) ON COMMIT DELETE ROWS",
                "REFUSED Unsupported feature 'ON COMMIT DELETE ROWS'."},
            {"CREATE OR REPLACE TABLE tq (a INT) COMMENT = 'c' ON COMMIT PRESERVE ROWS",
                "REFUSED SQL compilation error: | 'ON COMMIT' can only be used with temporary object"},
            {"SELECT 1 START TRANSACTION",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected "
                + "'TRANSACTION'. | syntax error line 1 at position 26 unexpected '<EOF>'."},
            {"SELECT 1 START",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '<EOF>'."},
            {"SELECT 1 START x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax"
                + " error line 1 at position 16 unexpected '<EOF>'."},
            {"SELECT 1 START FROM pt_t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'FROM'."},
            {"SELECT 1 start FROM pt_t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'FROM'."},
            {"SELECT 1 AS start",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'start'. | "
                + "syntax error line 1 at position 17 unexpected '<EOF>'."},
            {"SELECT 1 FOR i IN 1 TO 2 DO NULL; END FOR",
                "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'i'."},
            {"SELECT 1 FOR",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '<EOF>'."},
            {"SELECT 1 FOR FROM pt_t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'FROM'."},
            {"SELECT 1 AS for",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'for'. | "
                + "syntax error line 1 at position 15 unexpected '<EOF>'."},
            {"SELECT 1 SET",
                "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'SET'."},
            {"SELECT 1 NULL",
                "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'NULL'."},
            {"CREATE OR REPLACE VIEW v START = 1 AS SELECT 1 a",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'START'."},
            {"CREATE OR REPLACE VIEW v SET = 1 AS SELECT 1 a",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'SET'."},
            {"CREATE OR REPLACE VIEW v FOR = 1 AS SELECT 1 a",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'FOR'."},
            {"CREATE OR REPLACE VIEW v NULL = 1 AS SELECT 1 a",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'NULL'."},
            {"CREATE OR REPLACE VIEW v START AS SELECT 1 a",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'START'."},
            {"CREATE OR REPLACE TABLE tq USING TEMPLATE (SELECT ARRAY_AGG(OBJECT_CONSTRUCT('COLUMN_NAME', "
                + "'A', 'TYPE', 'INT', 'NULLABLE', TRUE))) x = 1",
                "REFUSED Unsupported feature 'Table function must be used in the TEMPLATE sub-query'."},
            {"SELECT * FROM (SELECT i FROM lk WHER i = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'i'."},
        });
    }

    @Test
    public void neighbours() {
        engine.execute("CREATE OR REPLACE TABLE pt_t (i INT)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER x 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'x'. | syntax"
                + " error line 1 at position 45 unexpected '1'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER x 'v'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'x'. | syntax"
                + " error line 1 at position 45 unexpected ''v''."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER x y z w",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'x'. | syntax"
                + " error line 1 at position 45 unexpected 'y'. | syntax error line 1 at position 49 unexpected"
                + " 'w'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER x y z = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'x'. | syntax"
                + " error line 1 at position 45 unexpected 'y'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER x y = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'x'. | syntax"
                + " error line 1 at position 45 unexpected 'y'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER x y z w v",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'x'. | syntax"
                + " error line 1 at position 45 unexpected 'y'. | syntax error line 1 at position 49 unexpected"
                + " 'w'. | syntax error line 1 at position 52 unexpected '<EOF>'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER x y;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'x'. | syntax"
                + " error line 1 at position 45 unexpected 'y'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER x y; SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'x'. | syntax"
                + " error line 1 at position 45 unexpected 'y'. | syntax error line 1 at position 59 unexpected"
                + " 'y'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER x SHOW",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'x'. | syntax"
                + " error line 1 at position 45 unexpected 'SHOW'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER x ON",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'x'. | syntax"
                + " error line 1 at position 45 unexpected 'ON'. | syntax error line 1 at position 47 "
                + "unexpected '<EOF>'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER x SET",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'x'. | syntax"
                + " error line 1 at position 45 unexpected 'SET'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER x (a)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'x'. | syntax"
                + " error line 1 at position 45 unexpected '('."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER x y (a)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'x'. | syntax"
                + " error line 1 at position 45 unexpected 'y'. | syntax error line 1 at position 47 unexpected"
                + " '('."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER x COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'x'. | syntax"
                + " error line 1 at position 45 unexpected 'COMMENT'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER SHOW",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'SHOW'. | "
                + "syntax error line 1 at position 47 unexpected '<EOF>'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER (a)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '('. | syntax"
                + " error line 1 at position 45 unexpected ')'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '1'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER SET",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'SET'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CLUSTER BY x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'x'. | syntax"
                + " error line 1 at position 47 unexpected '<EOF>'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CHANGE_TRACKING = ON x y z",
                "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'ON'. | "
                + "syntax error line 1 at position 56 unexpected 'x'. | syntax error line 1 at position 58 "
                + "unexpected 'y'. | syntax error line 1 at position 61 unexpected '<EOF>'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CHANGE_TRACKING = SET x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'SET'. | "
                + "syntax error line 1 at position 59 unexpected 'y'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CHANGE_TRACKING = SET = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'SET'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CHANGE_TRACKING = ON COMMIT PRESERVE ROWS",
                "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'ON'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CHANGE_TRACKING = ON (a)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'ON'. | "
                + "syntax error line 1 at position 56 unexpected '('. | syntax error line 1 at position 58 "
                + "unexpected ')'."},
            {"CREATE OR REPLACE TABLE tq (a INT) CHANGE_TRACKING = x",
                "REFUSED SQL compilation error: | invalid value [x] for parameter 'CHANGE_TRACKING'"},
            {"CREATE OR REPLACE TABLE tq (a INT) CHANGE_TRACKING = SHOW",
                "REFUSED SQL compilation error: | invalid value [SHOW] for parameter 'CHANGE_TRACKING'"},
            {"CREATE OR REPLACE TABLE tq (a INT) CHANGE_TRACKING = = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected '='."},
            {"CREATE OR REPLACE TABLE tq (a INT) COMMENT = 'c' CHANGE_TRACKING = ON x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 67 unexpected 'ON'. | "
                + "syntax error line 1 at position 70 unexpected 'x'. | syntax error line 1 at position 71 "
                + "unexpected '<EOF>'."},
            {"CREATE OR REPLACE TABLE tq CHANGE_TRACKING = ON (a INT)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'ON'. | "
                + "syntax error line 1 at position 48 unexpected '('."},
            {"CREATE OR REPLACE TABLE tq CHANGE_TRACKING = ON AS SELECT 1 a",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'ON'. | "
                + "syntax error line 1 at position 48 unexpected 'AS'."},
            {"CREATE OR REPLACE TABLE tq (a INT) COPY x y z",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'x'. | syntax"
                + " error line 1 at position 42 unexpected 'y'. | syntax error line 1 at position 45 unexpected"
                + " '<EOF>'."},
            {"CREATE OR REPLACE TABLE tq (a INT) COPY x ()",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'x'. | syntax"
                + " error line 1 at position 42 unexpected '('."},
            {"CREATE OR REPLACE TABLE tq (a INT) foo x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'x'. | syntax"
                + " error line 1 at position 42 unexpected '<EOF>'."},
            {"CREATE OR REPLACE TABLE tq (a INT) foo x y z",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'x'. | syntax"
                + " error line 1 at position 43 unexpected 'z'."},
            {"CREATE OR REPLACE TABLE tq (a INT) foo x (1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'x'. | syntax"
                + " error line 1 at position 41 unexpected '('."},
            {"CREATE OR REPLACE TABLE tq (a INT) ON x y z",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'x'. | syntax"
                + " error line 1 at position 40 unexpected 'y'. | syntax error line 1 at position 43 unexpected"
                + " '<EOF>'."},
            {"CREATE OR REPLACE TABLE tq (a INT) ON COMMIT PRESERVE x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected 'x'. | syntax"
                + " error line 1 at position 55 unexpected '<EOF>'."},
            {"CREATE OR REPLACE TRANSIENT TABLE tq (a INT) ON COMMIT PRESERVE ROWS",
                "REFUSED SQL compilation error: | 'ON COMMIT' can only be used with temporary object"},
            {"CREATE OR REPLACE TABLE tq (a INT) foo = 1 ON COMMIT PRESERVE ROWS",
                "REFUSED SQL compilation error: | 'ON COMMIT' can only be used with temporary object"},
            {"CREATE OR REPLACE TEMPORARY TABLE tq (a INT) foo = 1 ON COMMIT DELETE ROWS",
                "REFUSED Unsupported feature 'ON COMMIT DELETE ROWS'."},
            {"CREATE OR REPLACE TEMPORARY VIEW vq ON COMMIT PRESERVE ROWS AS SELECT 1 a",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'ON'."},
            {"CREATE OR REPLACE TABLE nosuchdb.s.tq (a INT) ON COMMIT PRESERVE ROWS",
                "REFUSED SQL compilation error: | 'ON COMMIT' can only be used with temporary object"},
            {"ALTER TABLE pt_t SET ON COMMIT PRESERVE ROWS",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ON'."},
            {"CREATE OR REPLACE TABLE tq (a INT) WITH x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'y'."},
            {"SELECT 1 START x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax"
                + " error line 1 at position 17 unexpected 'y'."},
            {"SELECT 1 START x;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax"
                + " error line 1 at position 16 unexpected ';'."},
            {"SELECT 1 START x CONNECT BY 1 = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'."},
            {"SELECT 1 START 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'. | syntax"
                + " error line 1 at position 16 unexpected '<EOF>'."},
            {"SELECT 1 START = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '='."},
            {"SELECT 1 START WITH",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '<EOF>'."},
            {"SELECT 1 START TRANSACTION; SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected "
                + "'TRANSACTION'. | syntax error line 1 at position 26 unexpected ';'. | syntax error line 1 at"
                + " position 39 unexpected 'y'."},
            {"SELECT i FROM pt_t START TRANSACTION",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected "
                + "'TRANSACTION'. | syntax error line 1 at position 36 unexpected '<EOF>'."},
            {"SELECT i FROM pt_t START x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'. | syntax"
                + " error line 1 at position 26 unexpected '<EOF>'."},
            {"SELECT i FROM pt_t p START TRANSACTION",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected "
                + "'TRANSACTION'. | syntax error line 1 at position 38 unexpected '<EOF>'."},
            {"SELECT i FROM pt_t WHERE i = 1 START TRANSACTION",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected "
                + "'TRANSACTION'. | syntax error line 1 at position 48 unexpected '<EOF>'."},
            {"SELECT 1 AS start x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'start'. | "
                + "syntax error line 1 at position 18 unexpected 'x'. | syntax error line 1 at position 19 "
                + "unexpected '<EOF>'."},
            {"SELECT 1 AS start FROM pt_t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'start'. | "
                + "syntax error line 1 at position 18 unexpected 'FROM'."},
            {"SELECT 1 FOR x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'."},
            {"SELECT 1 FOR x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'."},
            {"SELECT 1 FOR UPDATE",
                "REFUSED A 'FOR UPDATE' clause is not compatible with 'not selecting from a table'."},
            {"SELECT 1 FOR;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected ';'."},
            {"SELECT i FROM pt_t FOR i IN 1 TO 2 DO NULL; END FOR",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'i'."},
            {"SELECT i FROM pt_t FOR",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '<EOF>'."},
            {"SELECT 1 AS for x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'for'. | "
                + "syntax error line 1 at position 16 unexpected 'x'."},
            {"SELECT 1 AS for FROM pt_t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'for'. | "
                + "syntax error line 1 at position 16 unexpected 'FROM'."},
            {"CREATE OR REPLACE VIEW v START x AS SELECT 1 a",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'START'."},
            {"CREATE OR REPLACE VIEW v START = 1 x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'START'."},
            {"CREATE OR REPLACE VIEW v START WITH 1 AS SELECT 1 a",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'START'."},
            {"CREATE OR REPLACE VIEW v SET x = 1 AS SELECT 1 a",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'SET'."},
            {"CREATE OR REPLACE VIEW v FOR x = 1 AS SELECT 1 a",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'FOR'."},
        });
    }

    @Test
    public void aTemporaryTableTakesOnCommitPreserveRows() {
        engine.execute("CREATE OR REPLACE TABLE pt_t (i INT)");
        final String[] accepted = {
            "CREATE OR REPLACE TEMPORARY TABLE tq (a INT) ON COMMIT PRESERVE ROWS",
            "CREATE OR REPLACE TEMPORARY TABLE tq (a INT) ON COMMIT PRESERVE ROWS COMMENT = 'c'",
            "CREATE OR REPLACE TEMPORARY TABLE tq (a INT) ON COMMIT PRESERVE ROWS ON COMMIT PRESERVE ROWS",
            "CREATE OR REPLACE TEMPORARY TABLE tq ON COMMIT PRESERVE ROWS (a INT)",
            "CREATE OR REPLACE TEMPORARY TABLE tq ON COMMIT PRESERVE ROWS AS SELECT 1 a",
            "CREATE OR REPLACE TEMPORARY TABLE tq (a) ON COMMIT PRESERVE ROWS AS SELECT 1 a",
            "CREATE OR REPLACE TEMPORARY TABLE tq LIKE pt_t ON COMMIT PRESERVE ROWS",
            "CREATE OR REPLACE TEMPORARY TABLE tq CLONE pt_t ON COMMIT PRESERVE ROWS",
            "CREATE OR REPLACE TEMPORARY TABLE tq (a INT) on commit preserve rows",
        };
        for (final String sql : accepted) {
            assertEquals("Table TQ successfully created.", answer(sql), sql);
        }
    }
}
