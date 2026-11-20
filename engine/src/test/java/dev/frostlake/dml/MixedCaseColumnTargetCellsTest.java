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
 * The columns an UPDATE's or a MERGE's SET, a MERGE's INSERT list and the column clauses of ALTER TABLE and COMMENT ON
 * COLUMN name are matched by their canonical names: neither {@code bee} nor {@code "bee"} reaches a column created as
 * {@code "Bee"}. A SET or INSERT target's qualifier is the name the target answers to, judged after every other name
 * the statement reads; a duplicate is named by its later spelling. A NOT NULL over a column holding a NULL is refused
 * with the column's own spelling, a semi-structured column taking it like any other. Answers measured on a live
 * account; the RENAME COLUMN and COMMENT ON COLUMN refusals agree in their text, their error code still differs.
 */
public class MixedCaseColumnTargetCellsTest extends MeasuredCellsTestSupport {

    @Test
    public void namesMatchCanonically() {
        engine.execute("CREATE OR REPLACE TABLE t(id INT, \"Bee\" INT, cc INT)");
        engine.execute("INSERT INTO t VALUES (1, 1, 1), (2, NULL, NULL)");
        engine.execute("CREATE OR REPLACE TABLE u(id INT, cc INT)");
        engine.execute("CREATE OR REPLACE TABLE rn(id INT, \"Bee\" INT, cc INT)");
        engine.execute("UPDATE t SET t.\"Bee\" = 7 WHERE id = 1");
        engine.execute("UPDATE t a SET a.cc = 3 WHERE id = 1");
        engine.execute("MERGE INTO t USING (SELECT 1 id) s ON t.id = s.id WHEN MATCHED THEN UPDATE SET t.\"Bee\" = "
            + "\"Bee\" + 1");
        engine.execute("MERGE INTO t a USING (SELECT 3 id) s ON a.id = s.id WHEN NOT MATCHED THEN INSERT (a.id, "
            + "\"Bee\", \"CC\") VALUES (s.id, 30, 31)");
        engine.execute("ALTER TABLE t ALTER COLUMN \"Bee\" COMMENT 'b'");
        engine.execute("COMMENT ON COLUMN t.cc IS 'c'");
        engine.execute("ALTER TABLE rn RENAME COLUMN \"Bee\" TO \"Zed\"");
        engine.execute("ALTER TABLE rn RENAME COLUMN CC TO dd");
        assertCells(new String[][] {
            {"SELECT LISTAGG(id || ':' || NVL(\"Bee\"::VARCHAR, '-') || ':' || NVL(cc::VARCHAR, '-'), ' ')"
                + " WITHIN GROUP (ORDER BY id) FROM t",
                "1:8:3 2:-:- 3:30:31"},
            {"SELECT LISTAGG(column_name || '=' || NVL(comment, '-'), ' ') WITHIN GROUP (ORDER BY "
                + "ordinal_position) FROM information_schema.columns WHERE table_name = 'T' AND table_schema = "
                + "CURRENT_SCHEMA()",
                "ID=- Bee=b CC=c"},
            {"SELECT LISTAGG(column_name, ' ') WITHIN GROUP (ORDER BY ordinal_position) FROM "
                + "information_schema.columns WHERE table_name = 'RN' AND table_schema = CURRENT_SCHEMA()",
                "ID Zed DD"},
            {"UPDATE t SET bee = 5",
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'BEE'"},
            {"UPDATE t SET \"bee\" = 9",
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier '\"bee\"'"},
            {"UPDATE t SET BEE = 9",
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'BEE'"},
            {"UPDATE t SET t.bee = 5",
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'T.BEE'"},
            {"UPDATE t SET \"cc\" = 5",
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier '\"cc\"'"},
            {"UPDATE t SET nosuch = 1",
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'NOSUCH'"},
            {"UPDATE t SET id = 1, bee = 5",
                "REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'BEE'"},
            {"UPDATE t SET x.bee = 5",
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'X.BEE'"},
            {"UPDATE t SET x.cc = 5",
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'X.CC'"},
            {"UPDATE t a SET t.cc = 5",
                "REFUSED SQL compilation error: error line 1 at position 15 | invalid identifier 'T.CC'"},
            {"UPDATE t a SET a.bee = 5",
                "REFUSED SQL compilation error: error line 1 at position 15 | invalid identifier 'A.BEE'"},
            {"UPDATE t SET x.\"Bee\" = 5",
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'X.\"Bee\"'"},
            {"UPDATE t SET \"t\".cc = 5",
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier '\"t\".CC'"},
            {"UPDATE t SET u.cc = 5 FROM u",
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'U.CC'"},
            {"UPDATE t SET t.\"bee\" = 5",
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'T.\"bee\"'"},
            {"UPDATE t SET id = 1, x.cc = 5",
                "REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'X.CC'"},
            {"UPDATE t SET cc = 1, cc = 2",
                "REFUSED SQL compilation error: | duplicate column name 'CC'"},
            {"UPDATE t SET \"Bee\" = 1, \"Bee\" = 2",
                "REFUSED SQL compilation error: | duplicate column name '\"Bee\"'"},
            {"MERGE INTO t USING (SELECT 1 id) s ON t.id = s.id WHEN MATCHED THEN UPDATE SET bee = 11",
                "REFUSED SQL compilation error: error line 1 at position 79 | invalid identifier 'BEE'"},
            {"MERGE INTO t USING (SELECT 1 id) s ON t.id = s.id WHEN MATCHED THEN UPDATE SET t.bee = 11",
                "REFUSED SQL compilation error: error line 1 at position 79 | invalid identifier 'T.BEE'"},
            {"MERGE INTO t USING (SELECT 2 id) s ON t.id = s.id WHEN NOT MATCHED THEN INSERT (id, bee) "
                + "VALUES (2, 12)",
                "REFUSED SQL compilation error: error line 1 at position 84 | invalid identifier 'BEE'"},
            {"MERGE INTO t USING (SELECT 3 id) s ON t.id = s.id WHEN NOT MATCHED THEN INSERT (id, \"bee\")"
                + " VALUES (2, 12)",
                "REFUSED SQL compilation error: error line 1 at position 84 | invalid identifier '\"bee\"'"},
            {"MERGE INTO t USING (SELECT 1 id) s ON t.id = s.id WHEN MATCHED THEN UPDATE SET nosuch = 11",
                "REFUSED SQL compilation error: error line 1 at position 79 | invalid identifier 'NOSUCH'"},
            {"MERGE INTO t USING (SELECT 4 id) s ON t.id = s.id WHEN NOT MATCHED THEN INSERT (id, nosuch) "
                + "VALUES (2, 12)",
                "REFUSED SQL compilation error: error line 1 at position 84 | invalid identifier 'NOSUCH'"},
            {"MERGE INTO t USING (SELECT 1 id) s ON t.id = s.id WHEN MATCHED THEN UPDATE SET s.bee = 11",
                "REFUSED SQL compilation error: error line 1 at position 79 | invalid identifier 'S.BEE'"},
            {"MERGE INTO t a USING (SELECT 1 id) s ON a.id = s.id WHEN MATCHED THEN UPDATE SET t.cc = 11",
                "REFUSED SQL compilation error: error line 1 at position 81 | invalid identifier 'T.CC'"},
            {"MERGE INTO t USING (SELECT 1 id) s ON t.id = s.id WHEN MATCHED THEN UPDATE SET \"t\".cc = 11",
                "REFUSED SQL compilation error: error line 1 at position 79 | invalid identifier '\"t\".CC'"},
            {"MERGE INTO t USING (SELECT 1 id) s ON t.id = s.id WHEN MATCHED THEN UPDATE SET t.\"cc\" = 11",
                "REFUSED SQL compilation error: error line 1 at position 79 | invalid identifier 'T.\"cc\"'"},
            {"MERGE INTO t USING (SELECT 1 id) s ON t.id = s.id WHEN NOT MATCHED THEN INSERT (id, \"cc\") "
                + "VALUES (1, 2)",
                "REFUSED SQL compilation error: error line 1 at position 84 | invalid identifier '\"cc\"'"},
            {"MERGE INTO t USING (SELECT 1 id) s ON t.id = s.id WHEN NOT MATCHED THEN INSERT "
                + "(\"x\".nosuch) VALUES (1)",
                "REFUSED SQL compilation error: error line 1 at position 80 | invalid identifier "
                + "'\"x\".NOSUCH'"},
            {"MERGE INTO t USING (SELECT 1 id) s ON t.id = s.id WHEN NOT MATCHED THEN INSERT (t.id, "
                + "t.\"bee\") VALUES (1, 2)",
                "REFUSED SQL compilation error: error line 1 at position 86 | invalid identifier 'T.\"bee\"'"},
            {"MERGE INTO t USING (SELECT 1 id) s ON t.id = s.id WHEN NOT MATCHED THEN INSERT (x.id, x.bee)"
                + " VALUES (1, 2)",
                "REFUSED SQL compilation error: error line 1 at position 86 | invalid identifier 'X.BEE'"},
            {"MERGE INTO t USING (SELECT * FROM VALUES (1)) s ON t.id = s.$1 WHEN MATCHED THEN UPDATE SET "
                + "bee = 1",
                "REFUSED SQL compilation error: error line 1 at position 92 | invalid identifier 'BEE'"},
            {"INSERT INTO t(id, bee) VALUES (5, 5)",
                "REFUSED SQL compilation error: error line 1 at position 18 | invalid identifier 'BEE'"},
            {"INSERT INTO t(id, \"cc\") VALUES (5, 5)",
                "REFUSED SQL compilation error: error line 1 at position 18 | invalid identifier '\"cc\"'"},
            {"ALTER TABLE t ALTER COLUMN bee SET NOT NULL",
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'BEE'"},
            {"ALTER TABLE t ALTER COLUMN \"bee\" SET NOT NULL",
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier '\"bee\"'"},
            {"ALTER TABLE t ALTER COLUMN bee DROP NOT NULL",
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'BEE'"},
            {"ALTER TABLE t ALTER COLUMN bee COMMENT 'x'",
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'BEE'"},
            {"ALTER TABLE t ALTER COLUMN bee SET DATA TYPE NUMBER(38,0)",
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'BEE'"},
            {"ALTER TABLE t MODIFY COLUMN bee SET NOT NULL",
                "REFUSED SQL compilation error: error line 1 at position 28 | invalid identifier 'BEE'"},
            {"ALTER TABLE t ALTER bee SET NOT NULL",
                "REFUSED SQL compilation error: error line 1 at position 20 | invalid identifier 'BEE'"},
            {"ALTER TABLE t ALTER COLUMN \"Bee\" SET NOT NULL",
                "REFUSED SQL compilation error: | Column 'Bee' contains null values. Not null constraint "
                + "cannot be added. | "},
            {"ALTER TABLE t ALTER COLUMN cc SET NOT NULL",
                "REFUSED SQL compilation error: | Column 'CC' contains null values. Not null constraint "
                + "cannot be added. | "},
            {"ALTER TABLE t ALTER COLUMN bee SET DEFAULT 3",
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'BEE'"},
            {"ALTER TABLE t ALTER COLUMN bee DROP DEFAULT",
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'BEE'"},
            {"ALTER TABLE t ALTER COLUMN bee UNSET COMMENT",
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'BEE'"},
            {"ALTER TABLE t ALTER COLUMN \"cc\" COMMENT 'y'",
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier '\"cc\"'"},
            {"ALTER TABLE t ALTER COLUMN id SET NOT NULL, bee DROP NOT NULL",
                "REFUSED SQL compilation error: error line 1 at position 44 | invalid identifier 'BEE'"},
            {"ALTER TABLE t ALTER COLUMN nosuch SET NOT NULL",
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'NOSUCH'"},
            {"ALTER TABLE t ALTER COLUMN \"bee\" SET DATA TYPE NUMBER(38,0)",
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier '\"bee\"'"},
            {"ALTER TABLE t ALTER (COLUMN bee DROP NOT NULL, COLUMN id SET NOT NULL)",
                "REFUSED SQL compilation error: error line 1 at position 28 | invalid identifier 'BEE'"},
            {"ALTER TABLE t ADD PRIMARY KEY (bee)",
                "REFUSED SQL compilation error: error line 1 at position 31 | invalid identifier 'BEE'"},
            {"ALTER TABLE t ADD UNIQUE (\"cc\")",
                "REFUSED SQL compilation error: error line 1 at position 26 | invalid identifier '\"cc\"'"},
            {"ALTER TABLE t CLUSTER BY (bee)",
                "REFUSED SQL compilation error: error line 1 at position 26 | invalid identifier 'BEE'"},
            {"ALTER TABLE t CLUSTER BY (\"bee\")",
                "REFUSED SQL compilation error: error line 1 at position 26 | invalid identifier '\"bee\"'"},
            {"CREATE OR REPLACE TABLE t2(id INT, \"Bee\" INT) CLUSTER BY (bee)",
                "REFUSED SQL compilation error: error line 1 at position 58 | invalid identifier 'BEE'"},
            {"ALTER TABLE t MODIFY bee COMMENT 'q'",
                "REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'BEE'"},
            {"ALTER TABLE t RENAME COLUMN bee TO zz",
                "REFUSED SQL compilation error: | Object 'BEE' does not exist or not authorized."},
            {"ALTER TABLE t RENAME COLUMN \"bee\" TO zz",
                "REFUSED SQL compilation error: | Object '\"bee\"' does not exist or not authorized."},
            {"ALTER TABLE t RENAME COLUMN \"cc\" TO zz",
                "REFUSED SQL compilation error: | Object '\"cc\"' does not exist or not authorized."},
            {"ALTER TABLE t RENAME COLUMN nosuch TO zz",
                "REFUSED SQL compilation error: | Object 'NOSUCH' does not exist or not authorized."},
            {"ALTER TABLE t RENAME COLUMN \"Bee\" TO \"Bee\"",
                "REFUSED SQL compilation error: | Object 'T.\"Bee\"' already exists."},
            {"ALTER TABLE t RENAME COLUMN cc TO \"Bee\"",
                "REFUSED SQL compilation error: | Object 'T.\"Bee\"' already exists."},
            {"ALTER TABLE t DROP COLUMN bee",
                "REFUSED SQL compilation error: | column 'BEE' does not exist"},
            {"ALTER TABLE t DROP COLUMN \"cc\"",
                "REFUSED SQL compilation error: | column 'cc' does not exist"},
            {"COMMENT ON COLUMN t.bee IS 'x'",
                "REFUSED SQL compilation error: | Object 'BEE' does not exist or not authorized."},
            {"COMMENT ON COLUMN t.\"bee\" IS 'x'",
                "REFUSED SQL compilation error: | Object 'bee' does not exist or not authorized."},
            {"COMMENT ON COLUMN t.\"cc\" IS 'x'",
                "REFUSED SQL compilation error: | Object 'cc' does not exist or not authorized."},
            {"SELECT bee FROM t",
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'BEE'"},
            {"UPDATE t SET t.\"Bee\" = 7, \"Bee\" = 7",
                "REFUSED SQL compilation error: | duplicate column name '\"Bee\"'"},
            {"MERGE INTO t USING (SELECT 1 id) s ON t.id = s.id WHEN MATCHED THEN UPDATE SET \"Bee\" = 1, "
                + "t.\"Bee\" = 2",
                "REFUSED SQL compilation error: | duplicate column name 'T.\"Bee\"'"},
            {"MERGE INTO t USING (SELECT 1 id) s ON t.id = s.id WHEN NOT MATCHED THEN INSERT (id, t.id) "
                + "VALUES (1, 2)",
                "REFUSED SQL compilation error: | duplicate column name 'T.ID'"},
            {"MERGE INTO t USING (SELECT 3 id) s ON t.id = s.id WHEN NOT MATCHED THEN INSERT (x.id) VALUES"
                + " (1)",
                "REFUSED SQL compilation error: error line 1 at position 80 | invalid identifier 'X.ID'"},
            {"MERGE INTO t a USING (SELECT 3 id) s ON a.id = s.id WHEN NOT MATCHED THEN INSERT (t.id) "
                + "VALUES (1)",
                "REFUSED SQL compilation error: error line 1 at position 82 | invalid identifier 'T.ID'"},
            {"MERGE INTO t USING (SELECT 3 id) s ON t.id = s.id WHEN NOT MATCHED THEN INSERT (t.id, x.cc) "
                + "VALUES (1, 2)",
                "REFUSED SQL compilation error: error line 1 at position 86 | invalid identifier 'X.CC'"},
            {"MERGE INTO t a USING (SELECT 3 id) s ON a.id = s.id WHEN NOT MATCHED THEN INSERT (s.id) "
                + "VALUES (1)",
                "REFUSED SQL compilation error: error line 1 at position 82 | invalid identifier 'S.ID'"},
            {"UPDATE t SET x.id = 1, nosuch = 2",
                "REFUSED SQL compilation error: error line 1 at position 23 | invalid identifier 'NOSUCH'"},
            {"MERGE INTO t USING (SELECT 1 id) s ON t.id = s.id WHEN MATCHED THEN UPDATE SET x.id = 1, "
                + "nosuch = 2",
                "REFUSED SQL compilation error: error line 1 at position 89 | invalid identifier 'NOSUCH'"},
            {"MERGE INTO t USING (SELECT 1 id) s ON t.id = s.id WHEN MATCHED THEN UPDATE SET x.id = 1 WHEN"
                + " NOT MATCHED THEN INSERT (nosuch) VALUES (1)",
                "REFUSED SQL compilation error: error line 1 at position 118 | invalid identifier 'NOSUCH'"},
            {"MERGE INTO t USING (SELECT 1 id) s ON t.id = s.id WHEN MATCHED THEN UPDATE SET x.id = 1 WHEN"
                + " NOT MATCHED THEN INSERT (id) VALUES (nosuch)",
                "REFUSED SQL compilation error: error line 1 at position 130 | invalid identifier 'NOSUCH'"},
            {"UPDATE t SET x.id = nosuch",
                "REFUSED SQL compilation error: error line 1 at position 20 | invalid identifier 'NOSUCH'"},
            {"UPDATE t SET x.id = 1, id = 2",
                "REFUSED SQL compilation error: | duplicate column name 'ID'"},
            {"UPDATE t SET id = 1, x.id = 2",
                "REFUSED SQL compilation error: | duplicate column name 'X.ID'"},
        });
    }
}
