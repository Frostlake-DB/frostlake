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
import org.junit.jupiter.api.Test;

/**
 * A correlated subquery the account cannot evaluate in an UPDATE ... FROM or a DELETE ... USING is refused where it is
 * written, in the SET list or in the WHERE, as the single-table statements and MERGE place it (live-verified).
 */
public class DmlJoinedSourceSubqueryPlaceTest extends MeasuredCellsTestSupport {

    @Test
    public void setAndWherePlaces() {
        engine.execute("CREATE TABLE tg(id INT, s STRING)");
        engine.execute("INSERT INTO tg VALUES (1,'a'),(2,'b'),(3,'c')");
        engine.execute("CREATE TABLE lk(k INT, label STRING)");
        engine.execute("INSERT INTO lk VALUES (1,'one'),(3,'three'),(3,'drei')");
        engine.execute("CREATE TABLE s2(sid INT, x STRING)");
        engine.execute("INSERT INTO s2 VALUES (1,'one'),(2,'two'),(3,'three')");
        assertCells(new String[][] {
            {"UPDATE tg FROM s2 SET s = (SELECT label FROM lk WHERE lk.k = s2.sid) WHERE tg.id = s2.sid",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 27"},
            {"UPDATE tg SET s = (SELECT label FROM lk WHERE lk.k = tg.id)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"UPDATE tg FROM s2 SET s = (SELECT label FROM lk WHERE lk.k = tg.id) WHERE tg.id = s2.sid",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 27"},
            {"UPDATE tg FROM s2\n  SET s = 'z',\n      id = (SELECT k FROM lk WHERE lk.label = s2.x)\n "
                + "WHERE tg.id = s2.sid",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 3, "
                + "position 12"},
            {"UPDATE tg FROM s2 SET s = UPPER((SELECT label FROM lk WHERE lk.k = s2.sid)) WHERE tg.id = "
                + "s2.sid",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 32"},
            {"UPDATE tg FROM s2 SET s = ((SELECT label FROM lk WHERE lk.k = s2.sid)) WHERE tg.id = s2.sid",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 27"},
            {"UPDATE tg FROM s2 SET s = 'q' WHERE tg.s = (SELECT label FROM lk WHERE lk.k = s2.sid)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 44"},
            {"UPDATE tg SET s = 'q' WHERE tg.s = (SELECT label FROM lk WHERE lk.k = tg.id)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 36"},
            {"UPDATE tg t FROM s2 s SET s = (SELECT label FROM lk WHERE lk.k = s.sid) WHERE t.id = s.sid",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"UPDATE tg FROM s2 SET s = (WITH c AS (SELECT 1 one) SELECT label FROM lk WHERE lk.k = "
                + "s2.sid) WHERE tg.id = s2.sid",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 27"},
            {"UPDATE tg FROM (SELECT * FROM s2) v SET s = (SELECT label FROM lk WHERE lk.k = v.sid) WHERE "
                + "tg.id = v.sid",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 45"},
            {"UPDATE tg FROM s2, lk l2 SET s = (SELECT label FROM lk WHERE lk.k = l2.k) WHERE tg.id = "
                + "s2.sid",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 34"},
            {"DELETE FROM tg USING s2 WHERE tg.s = (SELECT label FROM lk WHERE lk.k = s2.sid)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 38"},
            {"DELETE FROM tg WHERE tg.s = (SELECT label FROM lk WHERE lk.k = tg.id)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"DELETE FROM tg USING s2\n WHERE tg.id = s2.sid\n   AND tg.s = (SELECT label FROM lk WHERE "
                + "lk.k = s2.sid)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 3, "
                + "position 15"},
            {"DELETE FROM tg t USING s2 s WHERE t.s = (SELECT label FROM lk WHERE lk.k = s.sid)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 41"},
            {"MERGE INTO tg USING s2 ON tg.id = s2.sid WHEN MATCHED THEN UPDATE SET s = (SELECT label FROM"
                + " lk WHERE lk.k = s2.sid)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 75"},
            {"MERGE INTO tg\nUSING s2 ON tg.id = s2.sid\nWHEN MATCHED THEN UPDATE SET s = (SELECT label "
                + "FROM lk WHERE lk.k = s2.sid)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 3, "
                + "position 34"},
            {"MERGE INTO tg USING (SELECT * FROM s2) v ON tg.id = v.sid WHEN NOT MATCHED THEN INSERT "
                + "VALUES (v.sid, (SELECT label FROM lk WHERE lk.k = v.sid))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 103"},
        });
    }

    @Test
    public void predicatesAndShapes() {
        engine.execute("CREATE TABLE tg(id INT, s STRING)");
        engine.execute("INSERT INTO tg VALUES (1,'a'),(2,'b'),(3,'c')");
        engine.execute("CREATE TABLE lk(k INT, label STRING)");
        engine.execute("INSERT INTO lk VALUES (1,'one'),(3,'three'),(3,'drei')");
        engine.execute("CREATE TABLE s2(sid INT, x STRING)");
        engine.execute("INSERT INTO s2 VALUES (1,'one'),(2,'two'),(3,'three')");
        assertCells(new String[][] {
            {"UPDATE tg FROM s2 SET s = 'q' WHERE tg.id = s2.sid AND EXISTS (SELECT 1 FROM lk WHERE lk.k ="
                + " s2.sid LIMIT 1)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 55"},
            {"DELETE FROM tg USING s2 WHERE tg.id = s2.sid AND EXISTS (SELECT 1 FROM lk WHERE lk.k = "
                + "s2.sid LIMIT 1)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 49"},
            {"UPDATE tg FROM s2 SET s = 'q' WHERE\n  tg.id = s2.sid AND tg.s = (SELECT label FROM lk WHERE"
                + " lk.k = s2.sid)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 2, "
                + "position 29"},
            {"UPDATE tg FROM s2 SET s = 'q' WHERE tg.id = s2.sid\n  AND tg.s = (SELECT label FROM lk WHERE"
                + " lk.k = s2.sid)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 2, "
                + "position 14"},
            {"UPDATE tg FROM s2 SET s = 'q', id = id + (SELECT k FROM lk WHERE lk.label = s2.x) WHERE "
                + "tg.id = s2.sid",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 42"},
            {"UPDATE tg FROM s2 SET s = (SELECT label FROM lk WHERE lk.k = s2.sid) WHERE tg.id = s2.sid "
                + "AND tg.s = (SELECT label FROM lk WHERE lk.k = s2.sid)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 102"},
            {"UPDATE tg FROM s2 JOIN lk l2 ON l2.k = s2.sid SET s = (SELECT label FROM lk WHERE lk.k = "
                + "l2.k) WHERE tg.id = s2.sid",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 55"},
        });
    }
}
