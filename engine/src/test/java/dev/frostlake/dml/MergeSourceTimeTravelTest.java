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

package dev.frostlake.dml;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A MERGE source named as a table, a view or an IDENTIFIER() reference takes a time travel clause as a FROM item
 * does: {@code USING s AT(…)}, {@code BEFORE(…)} and {@code CHANGES(…)} read the source at that point, and the
 * source's alias follows the clause. A stream refuses it, and a clause after the alias or on a VALUES source is a
 * syntax error. Answers measured on a live account.
 */
public class MergeSourceTimeTravelTest extends MeasuredCellsTestSupport {

    private static final String TARGET_ROWS =
        "SELECT LISTAGG(id || ':' || v, ',') WITHIN GROUP (ORDER BY id) FROM mt";

    @BeforeEach
    public void createSource() {
        engine.execute("CREATE OR REPLACE TABLE mt (id INT, v INT)");
        engine.execute("CREATE OR REPLACE TABLE ms (id INT, v INT) CHANGE_TRACKING = TRUE");
        engine.execute("INSERT INTO ms VALUES (1, 100), (2, 200)");
        engine.execute("SET q1 = LAST_QUERY_ID()");
        engine.execute("INSERT INTO ms VALUES (3, 300)");
        engine.execute("SET q2 = LAST_QUERY_ID()");
    }

    @Test
    public void readsTheSourceAtThePoint() {
        engine.execute("MERGE INTO mt USING ms BEFORE(STATEMENT => $q2) s ON mt.id = s.id WHEN MATCHED THEN UPDATE "
            + "SET v = s.v WHEN NOT MATCHED THEN INSERT VALUES (s.id, s.v)");
        assertCells(new String[][] {{TARGET_ROWS, "1:100,2:200"}});
        engine.execute("TRUNCATE TABLE mt");
        engine.execute("MERGE INTO mt USING ms AT(STATEMENT => $q1) AS s ON mt.id = s.id WHEN NOT MATCHED THEN "
            + "INSERT VALUES (s.id, s.v)");
        assertCells(new String[][] {{TARGET_ROWS, "1:100,2:200"}});
        engine.execute("TRUNCATE TABLE mt");
        engine.execute("MERGE INTO mt USING ms AT(STATEMENT => $q1) AS s (x, y) ON mt.id = s.x WHEN NOT MATCHED "
            + "THEN INSERT VALUES (s.x, s.y)");
        assertCells(new String[][] {{TARGET_ROWS, "1:100,2:200"}});
    }

    @Test
    public void readsByOffsetAndTimestamp() {
        engine.execute("SELECT SYSTEM$WAIT(2)");
        engine.execute("MERGE INTO mt USING ms AT(OFFSET => -1) ON mt.id = ms.id WHEN NOT MATCHED THEN INSERT "
            + "VALUES (ms.id, ms.v)");
        assertCells(new String[][] {{TARGET_ROWS, "1:100,2:200,3:300"}});
        engine.execute("TRUNCATE TABLE mt");
        engine.execute("MERGE INTO mt USING ms AT(TIMESTAMP => DATEADD(second, -1, CURRENT_TIMESTAMP())) s ON "
            + "mt.id = s.id WHEN NOT MATCHED THEN INSERT VALUES (s.id, s.v)");
        assertCells(new String[][] {
            {TARGET_ROWS, "1:100,2:200,3:300"},
            {"MERGE INTO mt USING ms AT(OFFSET => -100000) s ON mt.id = s.id WHEN NOT MATCHED THEN INSERT "
                + "VALUES (s.id, s.v)",
                "REFUSED Time travel data is not available for table MS. The requested time is either beyond the "
                + "allowed time travel period or before the object creation time."},
        });
    }

    @Test
    public void readsAnIdentifierAViewAndChanges() {
        engine.execute("CREATE OR REPLACE VIEW mv AS SELECT id, v FROM ms");
        engine.execute("MERGE INTO mt USING IDENTIFIER('ms') BEFORE(STATEMENT => $q2) s ON mt.id = s.id WHEN NOT "
            + "MATCHED THEN INSERT VALUES (s.id, s.v)");
        assertCells(new String[][] {{TARGET_ROWS, "1:100,2:200"}});
        engine.execute("TRUNCATE TABLE mt");
        engine.execute("MERGE INTO mt USING mv BEFORE(STATEMENT => $q2) s ON mt.id = s.id WHEN NOT MATCHED THEN "
            + "INSERT VALUES (s.id, s.v)");
        assertCells(new String[][] {{TARGET_ROWS, "1:100,2:200"}});
        engine.execute("TRUNCATE TABLE mt");
        engine.execute("MERGE INTO mt USING ms CHANGES(INFORMATION => DEFAULT) AT(STATEMENT => $q1) s ON "
            + "mt.id = s.id WHEN NOT MATCHED THEN INSERT VALUES (s.id, s.v)");
        assertCells(new String[][] {{TARGET_ROWS, "3:300"}});
        engine.execute("TRUNCATE TABLE mt");
        engine.execute("MERGE INTO mt USING (SELECT * FROM ms AT(STATEMENT => $q1)) s ON mt.id = s.id WHEN NOT "
            + "MATCHED THEN INSERT VALUES (s.id, s.v)");
        assertCells(new String[][] {{TARGET_ROWS, "1:100,2:200"}});
    }

    @Test
    public void refusals() {
        engine.execute("INSERT INTO mt VALUES (1, 1)");
        engine.execute("MERGE INTO mt USING ms at ON mt.id = at.id WHEN NOT MATCHED THEN INSERT VALUES (at.id, at.v)");
        assertCells(new String[][] {
            {TARGET_ROWS, "1:1,2:200,3:300"},
            {"MERGE INTO mt USING ms BEFORE(STATEMENT => 'bogus-id') s ON mt.id = s.id WHEN MATCHED THEN DELETE",
                "REFUSED Statement bogus-id not found"},
            {"MERGE INTO mt USING (VALUES (1, 2)) AT(OFFSET => -1) s ON mt.id = s.column1 WHEN MATCHED THEN "
                + "DELETE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '=>'."},
        });
    }
}
