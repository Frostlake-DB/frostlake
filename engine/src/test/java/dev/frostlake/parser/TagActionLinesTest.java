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
 * The lines reported for an ALTER of a task, a stage, a table, a view, a schema or a database whose SET TAG or UNSET
 * TAG does not read whole (live-verified).
 */
public class TagActionLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void setTagOfEachKind() {
        engine.execute("CREATE TAG tg1");
        engine.execute("CREATE TASK tk1 AS SELECT 1");
        engine.execute("CREATE TABLE t1 (a INT)");
        engine.execute("CREATE VIEW v1 AS SELECT 1 x");
        engine.execute("CREATE STAGE st1");
        assertCells(new String[][] {
            {"ALTER TASK tk1 SET TAG tg1 = 'x' COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'tg1'."},
            {"ALTER TASK tk1 SET TAG tg1 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'tg1'. | "
                + "syntax error line 1 at position 27 unexpected ''x''."},
            {"ALTER TABLE t1 SET TAG tg1 = 'x' COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'COMMENT'."},
            {"ALTER VIEW v1 SET TAG tg1 = 'x' COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'COMMENT'."},
            {"ALTER SCHEMA test_schema SET TAG tg1 = 'x' COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'TAG'. | "
                + "syntax error line 1 at position 33 unexpected 'tg1'."},
            {"ALTER STAGE st1 SET COMMENT = 'c' TAG tg1 = 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'TAG'."},
            {"ALTER STAGE st1 SET TAG tg1 = 'x', tg1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'tg1'. | "
                + "syntax error line 1 at position 38 unexpected '<EOF>'."},
            {"ALTER STAGE st1 SET TAG tg1 = 'x',",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'tg1'. | "
                + "syntax error line 1 at position 34 unexpected '<EOF>'."},
            {"ALTER TASK tk1 SET TAG tg1 = 'x' x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'tg1'. | "
                + "syntax error line 1 at position 34 unexpected '<EOF>'."},
            {"ALTER TABLE t1 SET TAG tg1 = 'x' x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'x'."},
            {"ALTER TABLE t1 SET TAG tg1 = 'x' COMMENT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'COMMENT'."},
            {"ALTER TABLE t1 SET TAG tg1 = 'x', tg1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ','."},
            {"ALTER TABLE t1 SET TAG tg1 = 'x',",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ','."},
            {"ALTER TASK tk1 SET TAG tg1 = 'x',",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'tg1'. | "
                + "syntax error line 1 at position 33 unexpected '<EOF>'."},
            {"ALTER VIEW v1 SET TAG tg1 = 'x' x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'."},
            {"ALTER SCHEMA test_schema SET TAG tg1 = 'x' x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'TAG'. | "
                + "syntax error line 1 at position 33 unexpected 'tg1'."},
            {"ALTER TASK tk1 SET TAG tg1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'tg1'."},
            {"ALTER TABLE t1 SET TAG tg1 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ''x''."},
            {"ALTER VIEW v1 SET TAG tg1 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'TAG'."},
        });
    }

    @Test
    public void setTagReadOn() {
        engine.execute("CREATE TASK tk1 AS SELECT 1");
        engine.execute("CREATE STAGE st1");
        assertCells(new String[][] {
            {"ALTER SCHEMA test_schema SET FOO 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'FOO'."},
            {"ALTER SCHEMA test_schema SET TAG tg1 = 'x' x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'TAG'. | "
                + "syntax error line 1 at position 33 unexpected 'tg1'."},
            {"ALTER SCHEMA test_schema SET TAG tg1 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'TAG'. | "
                + "syntax error line 1 at position 33 unexpected 'tg1'. | syntax error line 1 at position 37 "
                + "unexpected ''x''."},
            {"ALTER SCHEMA test_schema SET TAG tg1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'TAG'. | "
                + "syntax error line 1 at position 33 unexpected 'tg1'."},
            {"ALTER SCHEMA test_schema SET TAG tg1 = 'x',",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'TAG'. | "
                + "syntax error line 1 at position 33 unexpected 'tg1'. | syntax error line 1 at position 43 "
                + "unexpected '<EOF>'."},
            {"ALTER SCHEMA test_schema SET TAG tg1 = 'x', tg2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'TAG'. | "
                + "syntax error line 1 at position 33 unexpected 'tg1'. | syntax error line 1 at position 47 "
                + "unexpected '<EOF>'."},
            {"ALTER SCHEMA test_schema SET TAG tg1 = 'x' COMMENT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'TAG'. | "
                + "syntax error line 1 at position 33 unexpected 'tg1'."},
            {"ALTER TASK tk1 SET TAG tg1 = 'x', tg2 COMMENT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'tg1'. | "
                + "syntax error line 1 at position 38 unexpected 'COMMENT'."},
            {"ALTER TASK tk1 SET TAG tg1 = 'x' COMMENT = 'c' x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'tg1'. | "
                + "syntax error line 1 at position 48 unexpected '<EOF>'."},
            {"ALTER STAGE st1 SET TAG tg1 = 'x' COMMENT = 'c' x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'tg1'. | "
                + "syntax error line 1 at position 49 unexpected '<EOF>'."},
            {"ALTER TASK tk1 UNSET TAG tg1 COMMENT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'tg1'."},
            {"ALTER TASK tk1 UNSET TAG tg1 x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'tg1'."},
            {"ALTER SCHEMA test_schema UNSET TAG tg1 COMMENT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'TAG'. | "
                + "syntax error line 1 at position 35 unexpected 'tg1'."},
            {"ALTER TASK tk1 SET TAG tg1 = 'x' = 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'tg1'. | "
                + "syntax error line 1 at position 33 unexpected '='."},
        });
    }

    @Test
    public void tagListShapes() {
        engine.execute("CREATE TASK tk1 AS SELECT 1");
        engine.execute("CREATE TABLE t1 (a INT)");
        engine.execute("CREATE VIEW v1 AS SELECT 1 x");
        engine.execute("CREATE TAG tg1");
        assertCells(new String[][] {
            {"ALTER TABLE t1 SET TAG tg1 = 'x', tg1 =",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '<EOF>'."},
            {"ALTER TABLE t1 SET TAG tg1 = 'x', tg1 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ','."},
            {"ALTER TABLE t1 SET TAG tg1 = 'x',,",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ','."},
            {"ALTER TABLE t1 SET TAG tg1 =",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '<EOF>'."},
            {"ALTER TABLE t1 SET TAG tg1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'."},
            {"ALTER TABLE t1 SET TAG tg1 = 'x' x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'x'."},
            {"ALTER TABLE t1 SET TAG tg1 = 'x', tg1 = 'y' x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'x'."},
            {"ALTER TABLE t1 SET TAG tg1 = 'x', tg1 = 'y',",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected ','."},
            {"ALTER TABLE t1 SET TAG tg1 = 'x' = 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '='."},
            {"ALTER TABLE t1 SET TAG tg1.x 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ''y''."},
            {"ALTER TABLE t1 SET TAG tg1 = 'x', 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ','."},
            {"ALTER VIEW v1 SET TAG tg1 = 'x', tg1 =",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'TAG'."},
            {"ALTER VIEW v1 SET TAG tg1 = 'x', tg1 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ','."},
            {"ALTER VIEW v1 SET TAG tg1 = 'x',,",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ','."},
            {"ALTER VIEW v1 SET TAG tg1 =",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'TAG'."},
            {"ALTER VIEW v1 SET TAG tg1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'TAG'."},
            {"ALTER VIEW v1 SET TAG tg1 = 'x' x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'."},
            {"ALTER VIEW v1 SET TAG tg1 = 'x', tg1 = 'y' x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'x'."},
            {"ALTER VIEW v1 SET TAG tg1 = 'x', tg1 = 'y',",
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ','."},
            {"ALTER VIEW v1 SET TAG tg1 = 'x' = 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '='."},
            {"ALTER VIEW v1 SET TAG tg1.x 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'TAG'."},
            {"ALTER VIEW v1 SET TAG tg1 = 'x', 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ','."},
            {"ALTER SCHEMA test_schema UNSET TAG tg1, tg2 x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'TAG'. | "
                + "syntax error line 1 at position 35 unexpected 'tg1'."},
            {"ALTER SCHEMA test_schema UNSET TAG tg1 x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'TAG'. | "
                + "syntax error line 1 at position 35 unexpected 'tg1'."},
            {"ALTER SCHEMA test_schema UNSET TAG tg1,",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'TAG'. | "
                + "syntax error line 1 at position 35 unexpected 'tg1'."},
            {"ALTER SCHEMA test_schema UNSET TAG tg1, tg2 = 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'TAG'. | "
                + "syntax error line 1 at position 35 unexpected 'tg1'."},
            {"ALTER SCHEMA test_schema SET TAG tg1 = 'x', tg2 = 'y' x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'TAG'. | "
                + "syntax error line 1 at position 33 unexpected 'tg1'. | syntax error line 1 at position 50 "
                + "unexpected ''y''."},
            {"ALTER SCHEMA test_schema SET TAG tg1 = 'x', tg2 = 'y',",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'TAG'. | "
                + "syntax error line 1 at position 33 unexpected 'tg1'. | syntax error line 1 at position 50 "
                + "unexpected ''y''. | syntax error line 1 at position 54 unexpected '<EOF>'."},
            {"ALTER SCHEMA test_schema SET TAG tg1 = 'x', tg2 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'TAG'. | "
                + "syntax error line 1 at position 33 unexpected 'tg1'. | syntax error line 1 at position 48 "
                + "unexpected ''y''."},
            {"ALTER SCHEMA test_schema SET TAG tg1 = 'x' = 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'TAG'. | "
                + "syntax error line 1 at position 33 unexpected 'tg1'."},
            {"ALTER SCHEMA test_schema SET TAG tg1 =",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'TAG'. | "
                + "syntax error line 1 at position 33 unexpected 'tg1'."},
            {"ALTER SCHEMA test_schema SET TAG tg1 = 'x',,",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'TAG'. | "
                + "syntax error line 1 at position 33 unexpected 'tg1'. | syntax error line 1 at position 43 "
                + "unexpected ','."},
            {"ALTER DATABASE PRB_EAJ SET TAG tg1 = 'x' x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'TAG'."},
            {"ALTER DATABASE PRB_EAJ SET TAG tg1 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'TAG'."},
            {"ALTER DATABASE PRB_EAJ UNSET TAG tg1 x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'TAG'."},
        });
    }

    @Test
    public void tagAfterProperty() {
        engine.execute("CREATE TASK tk1 AS SELECT 1");
        engine.execute("CREATE STAGE st1");
        engine.execute("CREATE TAG tg1");
        assertCells(new String[][] {
            {"ALTER TASK tk1 SET COMMENT = 'c' TAG tg1 = 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'TAG'."},
            {"ALTER STAGE st1 SET COMMENT = 'c' TAG tg1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'TAG'."},
            {"ALTER STAGE st1 SET COMMENT = 'c', TAG tg1 = 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'tg1'."},
            {"ALTER TASK tk1 SET COMMENT = 'c', TAG tg1 = 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'tg1'."},
            {"ALTER STAGE st1 SET COMMENT = 'c' TAG = 'x'",
                "REFUSED SQL compilation error: | invalid property 'TAG' for 'STAGE'"},
            {"ALTER TASK tk1 SET COMMENT = 'c' TAG = 'x'",
                "REFUSED SQL compilation error: | invalid property 'TAG' for 'TASK'"},
            {"ALTER TASK tk1 SET TAG tg1 = 'x', tg2 = 'y' x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'tg1'. | "
                + "syntax error line 1 at position 45 unexpected '<EOF>'."},
            {"ALTER STAGE st1 SET TAG tg1 = 'x' COMMENT = 'c' x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'tg1'. | "
                + "syntax error line 1 at position 49 unexpected '<EOF>'."},
            {"ALTER TASK tk1 SET TAG tg1 = 'x' 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'tg1'. | "
                + "syntax error line 1 at position 33 unexpected ''y''."},
            {"ALTER TASK tk1 UNSET TAG tg1 COMMENT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'tg1'."},
            {"ALTER TASK tk1 SET TAG tg1 = 'x' SCHEDULE = '1 minute' x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'tg1'. | "
                + "syntax error line 1 at position 56 unexpected '<EOF>'."},
            {"ALTER VIEW v1 SET TAG tg1 = 'x' x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'."},
            {"ALTER DATABASE PRB_EAJ SET TAG tg1 = 'x', tg2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'TAG'."},
        });
    }
}
