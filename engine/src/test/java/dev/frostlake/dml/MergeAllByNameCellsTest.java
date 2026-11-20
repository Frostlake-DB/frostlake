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
 * MERGE's {@code WHEN MATCHED THEN UPDATE ALL BY NAME} and {@code WHEN NOT MATCHED THEN INSERT ALL BY NAME}: every
 * target column is written from the source column of the same canonical name, whatever the source's column order,
 * and a source of another column count, or one missing a target column, is refused as the statement compiles.
 * Answers measured on a live account.
 */
public class MergeAllByNameCellsTest extends MeasuredCellsTestSupport {

    @Test
    public void writesByName() {
        engine.execute("CREATE OR REPLACE TABLE tg(id INT, v INT, \"Mx\" VARCHAR, w VARCHAR)");
        engine.execute("INSERT INTO tg VALUES (1, 10, 'a', 'x'), (2, 20, 'b', 'y')");
        engine.execute("MERGE INTO tg USING (SELECT 1 id, 99 v, 'm' \"Mx\", 'z' w UNION ALL SELECT 3, 30, 'n', 'q') "
            + "s ON tg.id = s.id WHEN MATCHED THEN UPDATE ALL BY NAME WHEN NOT MATCHED THEN INSERT ALL BY "
            + "NAME");
        engine.execute("MERGE INTO tg USING (SELECT 'zz' w, 30 v, 3 id, 'o' \"Mx\") s ON tg.id = s.id WHEN MATCHED "
            + "THEN UPDATE ALL BY NAME");
        engine.execute("MERGE INTO tg AS t USING (SELECT 2 id, '66' v, 'e' w, 'q' \"Mx\") AS s ON t.id = s.id WHEN "
            + "MATCHED AND s.v > 0 THEN UPDATE ALL BY NAME");
        engine.execute("MERGE INTO tg USING (SELECT 7 id, '12' v, 'e' w, NULL \"Mx\") s ON tg.id = s.id WHEN NOT "
            + "MATCHED THEN INSERT ALL BY NAME");
        engine.execute("CREATE OR REPLACE TABLE src2(w VARCHAR, \"Mx\" VARCHAR, v INT, id INT)");
        engine.execute("INSERT INTO src2 VALUES ('w8', 'm8', 800, 8), ('w2', 'm2', 200, 2)");
        engine.execute("MERGE INTO tg USING src2 ON tg.id = src2.id WHEN MATCHED AND src2.id = 2 THEN UPDATE ALL BY "
            + "NAME WHEN NOT MATCHED THEN INSERT ALL BY NAME");
        engine.execute("CREATE OR REPLACE TABLE tq(\"a b\" INT, c INT)");
        engine.execute("MERGE INTO tq USING (SELECT 1 \"a b\", 2 c) s ON tq.c = s.c WHEN NOT MATCHED THEN INSERT ALL"
            + " BY NAME");
        assertCells(new String[][] {
            {"SELECT LISTAGG(id || ':' || v || ':' || NVL(\"Mx\", '-') || ':' || w, ' ') WITHIN GROUP "
                + "(ORDER BY id) FROM tg",
                "1:99:m:z 2:200:m2:w2 3:30:o:zz 7:12:-:e 8:800:m8:w8"},
            {"SELECT \"a b\" * 10 + c FROM tq",
                "12"},
            {"MERGE INTO tg USING (SELECT 1 id, 5 v) s ON tg.id = s.id WHEN MATCHED THEN UPDATE ALL BY "
                + "NAME",
                "REFUSED SQL compilation error: error line 1 at position 0 | Column count mismatch in MERGE "
                + "statement with ALL BY NAME subclause, source table has (2) columns but target table has (4)."},
            {"MERGE INTO tg USING (SELECT 4 id, 5 v) s ON tg.id = s.id WHEN NOT MATCHED THEN INSERT ALL BY"
                + " NAME",
                "REFUSED SQL compilation error: error line 1 at position 0 | Column count mismatch in MERGE "
                + "statement with ALL BY NAME subclause, source table has (2) columns but target table has (4)."},
            {"MERGE INTO tg USING (SELECT 1 id, 6 v, 'e' w, 'f' \"Mx\", 7 extra) s ON tg.id = s.id WHEN "
                + "MATCHED THEN UPDATE ALL BY NAME",
                "REFUSED SQL compilation error: error line 1 at position 0 | Column count mismatch in MERGE "
                + "statement with ALL BY NAME subclause, source table has (5) columns but target table has (4)."},
            {"MERGE INTO tg USING (SELECT 9 id, 6 v, 'e' w, 'f' \"Mx\", 7 extra) s ON tg.id = s.id WHEN "
                + "NOT MATCHED THEN INSERT ALL BY NAME",
                "REFUSED SQL compilation error: error line 1 at position 0 | Column count mismatch in MERGE "
                + "statement with ALL BY NAME subclause, source table has (5) columns but target table has (4)."},
            {"MERGE INTO tg USING (SELECT 1 id, 6 v, 'e' w, 'f' mx) s ON tg.id = s.id WHEN MATCHED THEN "
                + "UPDATE ALL BY NAME",
                "REFUSED SQL compilation error: error line 1 at position 0 | Missing column in MERGE "
                + "statement with ALL BY NAME subclause, the column Mx is in the target table but not the "
                + "source."},
            {"MERGE INTO tg USING (SELECT 9 id, 6 v, 'e' w, 'f' mx) s ON tg.id = s.id WHEN NOT MATCHED "
                + "THEN INSERT ALL BY NAME",
                "REFUSED SQL compilation error: error line 1 at position 0 | Missing column in MERGE "
                + "statement with ALL BY NAME subclause, the column Mx is in the target table but not the "
                + "source."},
            {"MERGE INTO tg USING (SELECT 1 \"id\", 6 \"v\", 'e' \"w\", 'f' \"Mx\") s ON tg.id = s.\"id\" "
                + "WHEN MATCHED THEN UPDATE ALL BY NAME",
                "REFUSED SQL compilation error: error line 1 at position 0 | Missing column in MERGE "
                + "statement with ALL BY NAME subclause, the column ID is in the target table but not the "
                + "source."},
            {"MERGE INTO tg USING (SELECT 1 ID, 6 V, 'e' W, 'f' \"MX\") s ON tg.id = s.id WHEN MATCHED "
                + "THEN UPDATE ALL BY NAME",
                "REFUSED SQL compilation error: error line 1 at position 0 | Missing column in MERGE "
                + "statement with ALL BY NAME subclause, the column Mx is in the target table but not the "
                + "source."},
            {"MERGE INTO tg USING (SELECT 2 id, 77 v) s ON tg.id = s.id WHEN MATCHED AND s.v > 0 THEN "
                + "UPDATE ALL BY NAME",
                "REFUSED SQL compilation error: error line 1 at position 0 | Column count mismatch in MERGE "
                + "statement with ALL BY NAME subclause, source table has (2) columns but target table has (4)."},
            {"MERGE INTO tg USING (SELECT 1 id, 6 v, 1 id) s ON tg.id = s.id WHEN MATCHED THEN UPDATE ALL "
                + "BY NAME",
                "REFUSED SQL compilation error: error line 1 at position 0 | Column count mismatch in MERGE "
                + "statement with ALL BY NAME subclause, source table has (3) columns but target table has (4)."},
            {"MERGE INTO tg USING (SELECT 1 id, 6 v) s ON tg.id = s.id WHEN MATCHED THEN UPDATE ALL BY "
                + "NAME WHEN MATCHED THEN DELETE",
                "REFUSED SQL compilation error: error line 1 at position 99 | Unreachable merge case."},
            {"MERGE INTO tg USING (SELECT 1 id, 6 v) s ON tg.id = s.id WHEN MATCHED THEN UPDATE ALL",
                "REFUSED SQL compilation error: | syntax error line 1 at position 85 unexpected '<EOF>'."},
            {"MERGE INTO tg USING (SELECT 1 id, 6 v) s ON tg.id = s.id WHEN NOT MATCHED THEN INSERT ALL",
                "REFUSED SQL compilation error: | syntax error line 1 at position 89 unexpected '<EOF>'."},
            {"MERGE INTO tg USING (SELECT 1 id, 6 v) s ON tg.id = s.id WHEN NOT MATCHED THEN INSERT (id) "
                + "ALL BY NAME",
                "REFUSED SQL compilation error: | syntax error line 1 at position 91 unexpected 'ALL'."},
            {"MERGE INTO tg USING (SELECT 1 id, 6 v) s ON tg.id = s.id WHEN MATCHED THEN UPDATE BY NAME",
                "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'BY'."},
            {"MERGE INTO tg USING (SELECT 1 id, 6 v, 'e' w, 1 id) s ON tg.id = 1 WHEN MATCHED THEN UPDATE "
                + "ALL BY NAME",
                "REFUSED SQL compilation error: error line 1 at position 0 | Missing column in MERGE "
                + "statement with ALL BY NAME subclause, the column Mx is in the target table but not the "
                + "source."},
            {"MERGE INTO tg USING (SELECT 1 a, 6 b, 'e' c, 1 d) s ON tg.id = s.a WHEN MATCHED THEN UPDATE "
                + "ALL BY NAME",
                "REFUSED SQL compilation error: error line 1 at position 0 | Missing column in MERGE "
                + "statement with ALL BY NAME subclause, the column ID is in the target table but not the "
                + "source."},
            {"MERGE INTO tg USING (SELECT 1 id, 6 b, 'e' c, 1 d) s ON tg.id = s.id WHEN MATCHED THEN "
                + "UPDATE ALL BY NAME",
                "REFUSED SQL compilation error: error line 1 at position 0 | Missing column in MERGE "
                + "statement with ALL BY NAME subclause, the column V is in the target table but not the "
                + "source."},
            {"MERGE INTO tg USING (SELECT 1 id, 'abc' v, 'e' w, 5 \"Mx\") s ON tg.id = s.id WHEN MATCHED "
                + "THEN UPDATE ALL BY NAME",
                "REFUSED DML operation to table TG failed on column V with error: Numeric value 'abc' is not "
                + "recognized"},
            {"  MERGE INTO tg USING (SELECT 1 a) s ON tg.id = s.a WHEN MATCHED THEN UPDATE ALL BY NAME",
                "REFUSED SQL compilation error: error line 1 at position 2 | Column count mismatch in MERGE "
                + "statement with ALL BY NAME subclause, source table has (1) columns but target table has (4)."},
            {"MERGE INTO tg USING (SELECT 1 a) s ON tg.id = s.a WHEN MATCHED THEN UPDATE SET v = 1 WHEN "
                + "NOT MATCHED THEN INSERT ALL BY NAME",
                "REFUSED SQL compilation error: error line 1 at position 0 | Column count mismatch in MERGE "
                + "statement with ALL BY NAME subclause, source table has (1) columns but target table has (4)."},
            {"MERGE INTO tg USING (SELECT $1 id, $2 v, $3 w, $4 \"Mx\" FROM VALUES (2, 5, 'p', 'q'), (2, "
                + "6, 'r', 's')) s ON tg.id = s.id WHEN MATCHED THEN UPDATE ALL BY NAME",
                "REFUSED Duplicate row detected during DML action | Row Values: [2, 200, \"m2\", \"w2\"]"},
            {"MERGE INTO tg USING (SELECT 1 id, 6 v, 'e' \"w\", 'q' \"mx\") s ON tg.id = s.id WHEN MATCHED"
                + " THEN UPDATE ALL BY NAME",
                "REFUSED SQL compilation error: error line 1 at position 0 | Missing column in MERGE "
                + "statement with ALL BY NAME subclause, the column Mx is in the target table but not the "
                + "source."},
            {"MERGE INTO tg USING (SELECT 1, 2, 3, 4) s ON tg.id = 1 WHEN MATCHED THEN UPDATE ALL BY NAME",
                "REFUSED SQL compilation error: error line 1 at position 0 | Missing column in MERGE "
                + "statement with ALL BY NAME subclause, the column ID is in the target table but not the "
                + "source."},
            {"MERGE INTO tg USING (SELECT 1 id, 6 v, 'e' w, 'q' \"Mx\") s ON tg.id = s.id WHEN NOT MATCHED"
                + " THEN INSERT ALL BY NAME WHEN NOT MATCHED THEN INSERT ALL BY NAME",
                "REFUSED SQL compilation error: error line 1 at position 120 | Unreachable merge case."},
            {"MERGE INTO tg USING (SELECT 1 id, 6 v, 'e' w, 'q' \"Mx\") s ON tg.id = s.id WHEN MATCHED "
                + "THEN UPDATE ALL BY NAME UNION",
                "REFUSED SQL compilation error: | syntax error line 1 at position 111 unexpected 'UNION'."},
            {"MERGE INTO tq USING (SELECT 1 x, 2 c) s ON tq.c = s.c WHEN NOT MATCHED THEN INSERT ALL BY "
                + "NAME",
                "REFUSED SQL compilation error: error line 1 at position 0 | Missing column in MERGE "
                + "statement with ALL BY NAME subclause, the column a b is in the target table but not the "
                + "source."},
        });
    }
}
