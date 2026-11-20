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
 * How a MERGE evaluates each target/source pair, measured on a live account: a bare name in the ON condition or a WHEN
 * condition reads the relation that has the column (the source alone in a WHEN NOT MATCHED condition), a correlated
 * scalar subquery the account cannot evaluate is refused as the statement compiles, a fault an ON or WHEN condition
 * raises fails the statement, and a target row that one source row updates while another updates or deletes it is
 * refused as nondeterministic unless ERROR_ON_NONDETERMINISTIC_MERGE is FALSE.
 */
public class MergePairEvaluationTest extends MeasuredCellsTestSupport {

    @Test
    public void sourceNamesResolveInConditions() {
        engine.execute("CREATE OR REPLACE TABLE tg(id INT, v INT)");
        engine.execute("INSERT INTO tg VALUES (1,10),(2,20)");
        engine.execute("CREATE OR REPLACE TABLE src(sid INT, sv INT, op STRING, x INT)");
        engine.execute("INSERT INTO src VALUES (1,5,'U',1),(5,8,'I',1)");
        engine.execute("CREATE OR REPLACE TABLE src2(id INT, v INT)");
        engine.execute("INSERT INTO src2 VALUES (1,7)");
        engine.execute("CREATE OR REPLACE TABLE src3(id INT, sv INT)");
        engine.execute("INSERT INTO src3 VALUES (1,5),(5,8),(6,9)");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN CREATE OR REPLACE TABLE tg(id INT, v INT); INSERT INTO tg VALUES "
                + "(1,10),(2,20); MERGE INTO tg USING src ON id = sid WHEN MATCHED THEN UPDATE SET v = src.sv "
                + "WHEN NOT MATCHED THEN INSERT (id, v) VALUES (src.sid, src.sv); RETURN (SELECT LISTAGG(id || "
                + "':' || v, ',') WITHIN GROUP (ORDER BY id, v) FROM tg); END; $$",
                "1:5,2:20,5:8"},
            {"EXECUTE IMMEDIATE $$ BEGIN CREATE OR REPLACE TABLE tg(id INT, v INT); INSERT INTO tg VALUES "
                + "(1,10),(2,20); MERGE INTO tg USING src ON tg.id = src.sid WHEN MATCHED AND op = 'U' THEN "
                + "UPDATE SET v = sv WHEN NOT MATCHED AND op = 'I' THEN INSERT (id, v) VALUES (sid, sv); RETURN"
                + " (SELECT LISTAGG(id || ':' || v, ',') WITHIN GROUP (ORDER BY id, v) FROM tg); END; $$",
                "1:5,2:20,5:8"},
            {"EXECUTE IMMEDIATE $$ BEGIN CREATE OR REPLACE TABLE tg(id INT, v INT); INSERT INTO tg VALUES "
                + "(1,10),(2,20); MERGE INTO tg t USING src s ON t.id = s.sid AND x = 1 WHEN MATCHED THEN "
                + "UPDATE SET v = 99; RETURN (SELECT LISTAGG(id || ':' || v, ',') WITHIN GROUP (ORDER BY id, v)"
                + " FROM tg); END; $$",
                "1:99,2:20"},
            {"EXECUTE IMMEDIATE $$ BEGIN CREATE OR REPLACE TABLE tg(id INT, v INT); INSERT INTO tg VALUES "
                + "(1,10),(2,20); MERGE INTO tg USING src3 ON tg.id = src3.id WHEN NOT MATCHED AND id = 5 THEN "
                + "INSERT (id, v) VALUES (id, sv); RETURN (SELECT LISTAGG(id || ':' || v, ',') WITHIN GROUP "
                + "(ORDER BY id, v) FROM tg); END; $$",
                "1:10,2:20,5:8"},
            {"EXECUTE IMMEDIATE $$ BEGIN CREATE OR REPLACE TABLE tg(id INT, v INT); INSERT INTO tg VALUES "
                + "(1,10),(2,20); MERGE INTO tg USING src3 ON tg.id = src3.id WHEN MATCHED AND sv < v THEN "
                + "UPDATE SET v = sv + tg.v * 2; RETURN (SELECT LISTAGG(id || ':' || v, ',') WITHIN GROUP "
                + "(ORDER BY id, v) FROM tg); END; $$",
                "1:25,2:20"},
            {"MERGE INTO tg USING src2 ON tg.id = src2.id AND v = 1 WHEN MATCHED THEN UPDATE SET v = 3",
                "REFUSED SQL compilation error: | ambiguous column name 'V'"},
            {"MERGE INTO tg USING src2 ON id = src2.id WHEN MATCHED THEN UPDATE SET v = 3",
                "REFUSED SQL compilation error: | ambiguous column name 'ID'"},
            {"MERGE INTO tg USING src2 ON tg.id = src2.id WHEN MATCHED AND v > 0 THEN UPDATE SET v = 3",
                "REFUSED SQL compilation error: | ambiguous column name 'V'"},
            {"MERGE INTO tg USING src2 ON tg.id = src2.id WHEN MATCHED THEN UPDATE SET v = v + 1",
                "REFUSED SQL compilation error: | ambiguous column name 'V'"},
            {"MERGE INTO tg USING src ON tg.id = src.sid WHEN MATCHED AND nosuch = 1 THEN UPDATE SET v = 1",
                "REFUSED SQL compilation error: error line 1 at position 60 | invalid identifier 'NOSUCH'"},
            {"MERGE INTO tg USING src ON tg.id = nosuch WHEN MATCHED THEN UPDATE SET v = 1",
                "REFUSED SQL compilation error: error line 1 at position 35 | invalid identifier 'NOSUCH'"},
            {"MERGE INTO tg USING src ON tg.id = src.sid WHEN NOT MATCHED AND tg.v = 1 THEN INSERT (id) "
                + "VALUES (1)",
                "REFUSED SQL compilation error: error line 1 at position 64 | invalid identifier 'TG.V'"},
            {"MERGE INTO tg USING src ON tg.id = src.sid WHEN NOT MATCHED AND v = 1 THEN INSERT (id) "
                + "VALUES (1)",
                "REFUSED SQL compilation error: error line 1 at position 64 | invalid identifier 'V'"},
        });
    }

    @Test
    public void correlatedScalarSubqueries() {
        engine.execute("CREATE OR REPLACE TABLE tg2(id INT, s STRING)");
        engine.execute("INSERT INTO tg2 VALUES (1,'a'),(2,'b')");
        engine.execute("CREATE OR REPLACE TABLE lk(k INT, label STRING)");
        engine.execute("INSERT INTO lk VALUES (1,'one'),(3,'three'),(2,'two')");
        engine.execute("CREATE OR REPLACE TABLE s2(sid INT, w STRING)");
        engine.execute("INSERT INTO s2 VALUES (1,'p'),(3,'q')");
        engine.execute("CREATE OR REPLACE TABLE e2(sid INT, w STRING)");
        engine.execute("CREATE OR REPLACE TABLE tg3(id INT, n INT)");
        engine.execute("INSERT INTO tg3 VALUES (1,0),(2,0)");
        assertCells(new String[][] {
            {"MERGE INTO tg2 USING (SELECT 1 sid UNION ALL SELECT 3) src ON tg2.id = src.sid WHEN MATCHED "
                + "THEN UPDATE SET s = (SELECT label FROM lk WHERE lk.k = src.sid) WHEN NOT MATCHED THEN INSERT"
                + " VALUES (src.sid, (SELECT label FROM lk WHERE lk.k = src.sid))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 203"},
            {"MERGE INTO tg2 USING (SELECT 1 sid UNION ALL SELECT 3) src ON tg2.id = src.sid WHEN MATCHED "
                + "THEN UPDATE SET s = (SELECT label FROM lk WHERE lk.k = src.sid LIMIT 1)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 113"},
            {"MERGE INTO tg3 USING (SELECT 1 sid UNION ALL SELECT 3) src ON tg3.id = src.sid WHEN MATCHED "
                + "THEN UPDATE SET n = (SELECT k FROM lk WHERE lk.k = src.sid)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 113"},
            {"MERGE INTO tg2 USING s2 ON tg2.id = s2.sid WHEN MATCHED THEN UPDATE SET s = (SELECT label "
                + "FROM lk WHERE lk.k = tg2.id)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 77"},
            {"MERGE INTO tg2 t USING s2 s ON t.id = s.sid WHEN MATCHED THEN UPDATE SET s = (SELECT label "
                + "FROM lk WHERE lk.k = s.sid AND lk.k = t.id)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 78"},
            {"MERGE INTO tg2 USING s2 ON tg2.id = s2.sid WHEN MATCHED AND (SELECT label FROM lk WHERE lk.k"
                + " = s2.sid) = 'one' THEN DELETE",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 61"},
            {"MERGE INTO tg2 USING s2 ON tg2.id = (SELECT k FROM lk WHERE lk.k = s2.sid) WHEN MATCHED THEN"
                + " DELETE",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 37"},
            {"MERGE INTO tg2 USING s2 ON tg2.id = s2.sid WHEN NOT MATCHED AND (SELECT label FROM lk WHERE "
                + "lk.k = s2.sid) = 'three' THEN INSERT VALUES (9, 'z')",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 65"},
            {"MERGE INTO tg2 USING s2 ON tg2.id = s2.sid WHEN MATCHED THEN UPDATE SET s = (SELECT w FROM "
                + "lk WHERE lk.k = 1)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 77"},
            {"MERGE INTO tg2 USING s2 ON tg2.id = (SELECT k FROM lk WHERE lk.k = s2.sid) WHEN MATCHED AND "
                + "(SELECT label FROM lk WHERE lk.k = s2.sid) = 'x' THEN UPDATE SET s = (SELECT label FROM lk "
                + "WHERE lk.k = s2.sid) WHEN NOT MATCHED AND (SELECT label FROM lk WHERE lk.k = s2.sid) = 'x' "
                + "THEN INSERT VALUES (1, (SELECT label FROM lk WHERE lk.k = s2.sid))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 37"},
            {"MERGE INTO tg2 USING s2 ON tg2.id = s2.sid WHEN MATCHED AND (SELECT label FROM lk WHERE lk.k"
                + " = s2.sid) = 'x' THEN UPDATE SET s = (SELECT label FROM lk WHERE lk.k = s2.sid)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 130"},
            {"MERGE INTO tg2 USING s2 ON tg2.id = s2.sid WHEN MATCHED AND (SELECT label FROM lk WHERE lk.k"
                + " = s2.sid) = 'x' THEN DELETE WHEN NOT MATCHED AND (SELECT label FROM lk WHERE lk.k = s2.sid)"
                + " = 'x' THEN INSERT VALUES (1, 'a')",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 61"},
            {"MERGE INTO tg2 USING s2 ON tg2.id = s2.sid WHEN NOT MATCHED AND (SELECT label FROM lk WHERE "
                + "lk.k = s2.sid) = 'x' THEN INSERT VALUES (1, (SELECT label FROM lk WHERE lk.k = s2.sid))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 137"},
            {"MERGE INTO tg2 USING s2 ON tg2.id = s2.sid WHEN NOT MATCHED THEN INSERT VALUES (1, (SELECT "
                + "label FROM lk WHERE lk.k = s2.sid)) WHEN MATCHED THEN UPDATE SET s = (SELECT label FROM lk "
                + "WHERE lk.k = s2.sid)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 84"},
            {"MERGE INTO tg2 USING s2 ON tg2.id = s2.sid + 100 WHEN MATCHED THEN UPDATE SET s = (SELECT "
                + "label FROM lk WHERE lk.k = s2.sid)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 83"},
            {"MERGE INTO tg2 USING e2 ON tg2.id = e2.sid WHEN MATCHED THEN UPDATE SET s = (SELECT label "
                + "FROM lk WHERE lk.k = tg2.id)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 77"},
            {"SELECT (SELECT label FROM lk WHERE lk.k = s2.sid) FROM s2",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"MERGE INTO tg2 USING s2 ON tg2.id = s2.sid WHEN MATCHED THEN UPDATE SET s = (SELECT label "
                + "FROM lk WHERE lk.k > 1)",
                "REFUSED Single-row subquery returns more than one row."},
            {"MERGE INTO tg2 USING s2 ON tg2.id = s2.sid WHEN NOT MATCHED THEN INSERT (id, s) VALUES (sid,"
                + " (SELECT k FROM lk))",
                "REFUSED Single-row subquery returns more than one row."},
            {"MERGE INTO tg2 USING s2 ON tg2.id = s2.sid WHEN NOT MATCHED THEN INSERT (id, s) VALUES (sid,"
                + " (SELECT 1 / 0))",
                "REFUSED DML operation to table TG2 failed on column S with error: Division by zero"},
            {"EXECUTE IMMEDIATE $$ BEGIN CREATE OR REPLACE TABLE tg2(id INT, s STRING); INSERT INTO tg2 "
                + "VALUES (1,'a'),(2,'b'); MERGE INTO tg2 USING e2 ON tg2.id = e2.sid WHEN MATCHED THEN UPDATE "
                + "SET s = (SELECT label FROM lk WHERE lk.k = e2.sid); RETURN (SELECT LISTAGG(id || ':' || s, "
                + "',') WITHIN GROUP (ORDER BY id) FROM tg2); END; $$",
                "1:a,2:b"},
            {"EXECUTE IMMEDIATE $$ BEGIN CREATE OR REPLACE TABLE tg2(id INT, s STRING); INSERT INTO tg2 "
                + "VALUES (1,'a'),(2,'b'); MERGE INTO tg2 USING s2 ON tg2.id = s2.sid WHEN MATCHED AND (SELECT "
                + "MAX(label) FROM lk WHERE lk.k = s2.sid) = 'one' THEN UPDATE SET s = 'm' WHEN NOT MATCHED AND"
                + " (SELECT MAX(label) FROM lk WHERE lk.k = s2.sid) = 'three' THEN INSERT VALUES (s2.sid, "
                + "(SELECT MAX(label) FROM lk WHERE lk.k = s2.sid)); RETURN (SELECT LISTAGG(id || ':' || s, "
                + "',') WITHIN GROUP (ORDER BY id) FROM tg2); END; $$",
                "1:m,2:b,3:three"},
            {"EXECUTE IMMEDIATE $$ BEGIN CREATE OR REPLACE TABLE tg2(id INT, s STRING); INSERT INTO tg2 "
                + "VALUES (1,'a'),(2,'b'); MERGE INTO tg2 t USING s2 s ON t.id = s.sid WHEN MATCHED THEN UPDATE"
                + " SET s = (SELECT MAX(label) FROM lk WHERE lk.k = t.id + s.sid); RETURN (SELECT LISTAGG(id ||"
                + " ':' || s, ',') WITHIN GROUP (ORDER BY id) FROM tg2); END; $$",
                "1:two,2:b"},
            {"EXECUTE IMMEDIATE $$ BEGIN CREATE OR REPLACE TABLE tg2(id INT, s STRING); INSERT INTO tg2 "
                + "VALUES (1,'a'),(2,'b'); MERGE INTO tg2 USING s2 ON tg2.id = s2.sid WHEN MATCHED AND EXISTS "
                + "(SELECT 1 FROM lk WHERE lk.k = s2.sid) THEN UPDATE SET s = 'ex' WHEN NOT MATCHED AND s2.sid "
                + "IN (SELECT k FROM lk WHERE lk.label = 'three') THEN INSERT VALUES (s2.sid, 'in'); RETURN "
                + "(SELECT LISTAGG(id || ':' || s, ',') WITHIN GROUP (ORDER BY id) FROM tg2); END; $$",
                "1:ex,2:b,3:in"},
            {"EXECUTE IMMEDIATE $$ BEGIN CREATE OR REPLACE TABLE tg2(id INT, s STRING); INSERT INTO tg2 "
                + "VALUES (1,'a'),(2,'b'); MERGE INTO tg2 USING s2 ON tg2.id = s2.sid WHEN MATCHED THEN UPDATE "
                + "SET s = (SELECT label FROM lk WHERE lk.k = 2) WHEN NOT MATCHED THEN INSERT VALUES (s2.sid, "
                + "(SELECT label FROM lk WHERE lk.k = 1)); RETURN (SELECT LISTAGG(id || ':' || s, ',') WITHIN "
                + "GROUP (ORDER BY id) FROM tg2); END; $$",
                "1:two,2:b,3:one"},
            {"EXECUTE IMMEDIATE $$ BEGIN CREATE OR REPLACE TABLE tg2(id INT, s STRING); INSERT INTO tg2 "
                + "VALUES (1,'a'),(2,'b'); MERGE INTO tg2 USING s2 ON tg2.id = s2.sid WHEN MATCHED THEN UPDATE "
                + "SET s = (SELECT COUNT(*) FROM lk WHERE lk.k >= tg2.id); RETURN (SELECT LISTAGG(id || ':' || "
                + "s, ',') WITHIN GROUP (ORDER BY id) FROM tg2); END; $$",
                "1:3,2:b"},
        });
    }

    @Test
    public void conditionFaultsFailTheStatement() {
        engine.execute("CREATE OR REPLACE TABLE tg4(id INT AUTOINCREMENT, k STRING, v INT DEFAULT 42)");
        engine.execute("INSERT INTO tg4(k) VALUES ('1'),('x')");
        engine.execute("CREATE OR REPLACE TABLE src4(n INT, sv INT)");
        engine.execute("INSERT INTO src4 VALUES (1,5),(2,6)");
        assertCells(new String[][] {
            {"MERGE INTO tg4 USING src4 ON tg4.k::INT = src4.n WHEN MATCHED THEN UPDATE SET v = 0 WHEN NOT"
                + " MATCHED THEN INSERT (k, v) VALUES ('new', src4.n)",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT LISTAGG(k || ':' || v, ',') WITHIN GROUP (ORDER BY id) FROM tg4",
                "1:42,x:42"},
            {"MERGE INTO tg4 USING src4 ON tg4.id = src4.n WHEN MATCHED AND 1/(src4.sv-5) = 1 THEN DELETE",
                "REFUSED Division by zero"},
            {"MERGE INTO tg4 USING src4 ON tg4.id = src4.n + 10 WHEN NOT MATCHED AND 1/(src4.sv-5) = 1 "
                + "THEN INSERT (k) VALUES ('z')",
                "REFUSED Division by zero"},
            {"MERGE INTO tg4 USING src4 ON tg4.id = src4.n WHEN MATCHED AND tg4.k::INT = 1 THEN UPDATE SET"
                + " v = 1",
                "REFUSED Numeric value 'x' is not recognized"},
            {"MERGE INTO tg4 USING src4 ON tg4.id = src4.n WHEN MATCHED AND src4.sv = 99 THEN UPDATE SET v"
                + " = 1 WHEN MATCHED AND 1/(src4.sv-5) = 1 THEN DELETE",
                "REFUSED Division by zero"},
            {"MERGE INTO tg4 USING src4 ON tg4.id = src4.n WHEN MATCHED THEN UPDATE SET v = 1 / (sv - 5)",
                "REFUSED Division by zero"},
            {"MERGE INTO tg4 USING src4 ON tg4.id = src4.n + 10 WHEN NOT MATCHED THEN INSERT (k, v) VALUES"
                + " ('n', 1 / (sv - 6))",
                "REFUSED DML operation to table TG4 failed on column V with error: Division by zero"},
            {"SELECT LISTAGG(k || ':' || v, ',') WITHIN GROUP (ORDER BY id) FROM tg4",
                "1:42,x:42"},
        });
    }

    @Test
    public void nondeterministicUpdatesAndDeletes() {
        engine.execute("CREATE OR REPLACE TABLE tg5(id INT, v INT)");
        engine.execute("INSERT INTO tg5 VALUES (1,10)");
        engine.execute("CREATE OR REPLACE TABLE src5(id INT, v INT)");
        engine.execute("INSERT INTO src5 VALUES (1,5),(1,6),(2,7)");
        engine.execute("CREATE OR REPLACE TABLE src6(id INT, v INT)");
        engine.execute("INSERT INTO src6 VALUES (2,6),(2,5)");
        assertCells(new String[][] {
            {"MERGE INTO tg5 USING src5 ON tg5.id = src5.id WHEN MATCHED AND src5.v = 5 THEN UPDATE SET v "
                + "= src5.v WHEN MATCHED THEN DELETE",
                "REFUSED Duplicate row detected during DML action | Row Values: [1, 10]"},
            {"MERGE INTO tg5 USING src5 ON tg5.id = src5.id WHEN MATCHED AND src5.v = 6 THEN DELETE WHEN "
                + "MATCHED THEN UPDATE SET v = src5.v",
                "REFUSED Duplicate row detected during DML action | Row Values: [1, 10]"},
            {"MERGE INTO tg5 USING src5 ON tg5.id = src5.id WHEN MATCHED THEN UPDATE SET v = src5.v",
                "REFUSED Duplicate row detected during DML action | Row Values: [1, 10]"},
            {"MERGE INTO tg5 USING (SELECT 1 id, 5 v UNION ALL SELECT 1, 5) s ON tg5.id = s.id WHEN "
                + "MATCHED AND s.v = 5 THEN UPDATE SET v = 6 WHEN MATCHED THEN DELETE",
                "REFUSED Duplicate row detected during DML action | Row Values: [1, 10]"},
            {"SELECT LISTAGG(id || ':' || v, ',') WITHIN GROUP (ORDER BY id, v) FROM tg5",
                "1:10"},
            {"EXECUTE IMMEDIATE $$ BEGIN CREATE OR REPLACE TABLE tg5(id INT, v INT); INSERT INTO tg5 "
                + "VALUES (1,10),(2,20); MERGE INTO tg5 USING src5 ON tg5.id = src5.id WHEN MATCHED THEN "
                + "DELETE; RETURN (SELECT LISTAGG(id || ':' || v, ',') WITHIN GROUP (ORDER BY id, v) FROM tg5);"
                + " END; $$",
                ""},
            {"EXECUTE IMMEDIATE $$ BEGIN CREATE OR REPLACE TABLE tg5(id INT, v INT); INSERT INTO tg5 "
                + "VALUES (1,10),(2,20); MERGE INTO tg5 USING src5 ON tg5.id = src5.id WHEN MATCHED AND src5.v "
                + "= 5 THEN UPDATE SET v = src5.v; RETURN (SELECT LISTAGG(id || ':' || v, ',') WITHIN GROUP "
                + "(ORDER BY id, v) FROM tg5); END; $$",
                "1:5,2:20"},
            {"EXECUTE IMMEDIATE $$ BEGIN CREATE OR REPLACE TABLE tg5(id INT, v INT); INSERT INTO tg5 "
                + "VALUES (1,10),(2,20); MERGE INTO tg5 USING src5 ON tg5.id = src5.id WHEN MATCHED AND src5.v "
                + "= 5 THEN UPDATE SET v = src5.v WHEN MATCHED AND src5.v = 99 THEN DELETE; RETURN (SELECT "
                + "LISTAGG(id || ':' || v, ',') WITHIN GROUP (ORDER BY id, v) FROM tg5); END; $$",
                "1:5,2:20"},
            {"EXECUTE IMMEDIATE $$ BEGIN CREATE OR REPLACE TABLE tg5(id INT, v INT); INSERT INTO tg5 "
                + "VALUES (1,10),(2,20); MERGE INTO tg5 USING src6 ON tg5.id = src6.id WHEN MATCHED AND src6.v "
                + "= 5 THEN UPDATE SET v = 555; RETURN (SELECT LISTAGG(id || ':' || v, ',') WITHIN GROUP (ORDER"
                + " BY id, v) FROM tg5); END; $$",
                "1:10,2:555"},
            {"EXECUTE IMMEDIATE $$ BEGIN CREATE OR REPLACE TABLE tg5(id INT, v INT); INSERT INTO tg5 "
                + "VALUES (1,10),(2,20); MERGE INTO tg5 USING src6 ON tg5.id = src6.id WHEN MATCHED AND src6.v "
                + "= 5 THEN DELETE WHEN MATCHED AND src6.v = 6 THEN DELETE; RETURN (SELECT LISTAGG(id || ':' ||"
                + " v, ',') WITHIN GROUP (ORDER BY id, v) FROM tg5); END; $$",
                "1:10"},
            {"EXECUTE IMMEDIATE $$ BEGIN CREATE OR REPLACE TABLE tg5(id INT, v INT); INSERT INTO tg5 "
                + "VALUES (1,10),(2,20); ALTER SESSION SET ERROR_ON_NONDETERMINISTIC_MERGE = FALSE; MERGE INTO "
                + "tg5 USING src5 ON tg5.id = src5.id WHEN MATCHED AND src5.v = 5 THEN UPDATE SET v = src5.v "
                + "WHEN MATCHED THEN DELETE; ALTER SESSION UNSET ERROR_ON_NONDETERMINISTIC_MERGE; RETURN "
                + "(SELECT COUNT(*) FROM tg5 WHERE id = 2); END; $$",
                "0"},
            {"EXECUTE IMMEDIATE $$ BEGIN CREATE OR REPLACE TABLE tg5(id INT, v INT); INSERT INTO tg5 "
                + "VALUES (1,10),(2,20); ALTER SESSION SET ERROR_ON_NONDETERMINISTIC_MERGE = FALSE; MERGE INTO "
                + "tg5 USING src5 ON tg5.id = src5.id WHEN MATCHED THEN UPDATE SET v = src5.v; ALTER SESSION "
                + "UNSET ERROR_ON_NONDETERMINISTIC_MERGE; RETURN (SELECT COUNT(*) FROM tg5); END; $$",
                "2"},
        });
    }
}
