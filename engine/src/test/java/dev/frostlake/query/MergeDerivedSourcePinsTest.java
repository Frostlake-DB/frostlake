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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A MERGE's correlated subquery reading a name of a derived source - a subquery's or a view's column - is evaluable
 * where what computes the column pins it to one value, as a stored source's column holding one value is: a literal,
 * a NULL, an expression over pinned columns, MAX, MIN or COUNT over a whole table, a UNION of one value, the first
 * branch of an EXCEPT or an INTERSECT, and the item of a join that the join does not make NULL. RANDOM, a column
 * varying over its rows, SUM, a GROUP BY of several groups and an aggregate under a WHERE that drops rows leave it
 * varying, and the subquery is refused in the ON condition, a WHEN condition, a SET value and an INSERT value alike
 * (all live-verified).
 */
public class MergeDerivedSourcePinsTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void createRelations() {
        engine.execute("CREATE OR REPLACE TABLE mdlk (k INT, label VARCHAR)");
        engine.execute("INSERT INTO mdlk VALUES (1, 'one'), (2, 'two'), (3, 'three')");
        engine.execute("CREATE OR REPLACE TABLE mds2 (sid INT)");
        engine.execute("INSERT INTO mds2 VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE mds1 (sid INT)");
        engine.execute("INSERT INTO mds1 VALUES (2)");
        engine.execute("CREATE OR REPLACE TABLE mdss (sid INT)");
        engine.execute("INSERT INTO mdss VALUES (2), (2)");
        engine.execute("CREATE OR REPLACE TABLE mdsv (sid VARCHAR)");
        engine.execute("INSERT INTO mdsv VALUES ('x'), ('y')");
        engine.execute("CREATE OR REPLACE TABLE mdsn (sid INT)");
        engine.execute("INSERT INTO mdsn VALUES (NULL), (NULL)");
        engine.execute("CREATE OR REPLACE VIEW mdv1 AS SELECT 2 sid");
    }

    /** Asserts a MERGE over a fresh target reads as measured. */
    private void assertMerge(final String expected, final String sql) {
        engine.execute("CREATE OR REPLACE TABLE mdt (id INT, s VARCHAR)");
        engine.execute("INSERT INTO mdt VALUES (1, 'a'), (2, 'b'), (3, 'c')");
        assertAnswer(expected, sql);
    }

    @Test
    public void aSubqueryAnywhereReadsAPinnedSourceName() {
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT 2 sid) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET s = "
                + "(SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT 1 + 2 sid) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET s ="
                + " (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 113",
            "MERGE INTO mdt USING (SELECT 1 sid UNION ALL SELECT 2) src ON mdt.id = src.sid WHEN MATCHED THEN"
                + " UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT sid FROM mds1) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET"
                + " s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 102",
            "MERGE INTO mdt USING (SELECT sid FROM mds2) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET"
                + " s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 116",
            "MERGE INTO mdt USING (SELECT sid FROM mds2 WHERE sid = 2) src ON mdt.id = src.sid WHEN MATCHED "
                + "THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT MAX(sid) sid FROM mds2) src ON mdt.id = src.sid WHEN MATCHED THEN "
                + "UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING mds1 src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET s = (SELECT label"
                + " FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 84",
            "MERGE INTO mdt USING mds2 src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET s = (SELECT label"
                + " FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 116",
            "MERGE INTO mdt USING (SELECT column1 sid FROM VALUES (2)) src ON mdt.id = src.sid WHEN MATCHED "
                + "THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 121",
            "MERGE INTO mdt USING (SELECT column1 sid FROM VALUES (1), (2)) src ON mdt.id = src.sid WHEN "
                + "MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (WITH w AS (SELECT 2 sid) SELECT sid FROM w) src ON mdt.id = src.sid WHEN "
                + "MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 132",
            "MERGE INTO mdt USING (WITH w AS (SELECT sid FROM mds2) SELECT sid FROM w) src ON mdt.id = "
                + "src.sid WHEN MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT 2 sid) src ON mdt.id = src.sid WHEN MATCHED AND (SELECT label FROM "
                + "mdlk WHERE mdlk.k = src.sid) = 'two' THEN UPDATE SET s = 'w1'");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 86",
            "MERGE INTO mdt USING (SELECT sid FROM mds2) src ON mdt.id = src.sid WHEN MATCHED AND (SELECT "
                + "label FROM mdlk WHERE mdlk.k = src.sid) = 'two' THEN UPDATE SET s = 'w2'");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT 9 sid) src ON mdt.id = src.sid WHEN NOT MATCHED AND (SELECT label "
                + "FROM mdlk WHERE mdlk.k = src.sid - 7) = 'two' THEN INSERT (id, s) VALUES (src.sid, 'w3')");
        assertMerge("0",
            "MERGE INTO mdt USING (SELECT 2 sid) src ON mdt.s = (SELECT label FROM mdlk WHERE mdlk.k = "
                + "src.sid) WHEN MATCHED THEN UPDATE SET s = 'o1'");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 60",
            "MERGE INTO mdt USING (SELECT sid FROM mds2) src ON mdt.s = (SELECT label FROM mdlk WHERE mdlk.k "
                + "= src.sid) WHEN MATCHED THEN UPDATE SET s = 'o2'");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT 3 sid) src ON mdt.id = src.sid + 10 WHEN NOT MATCHED THEN INSERT "
                + "(id, s) VALUES (src.sid + 20, (SELECT label FROM mdlk WHERE mdlk.k = src.sid))");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 133",
            "MERGE INTO mdt USING (SELECT sid FROM mds2) src ON mdt.id = src.sid + 10 WHEN NOT MATCHED THEN "
                + "INSERT (id, s) VALUES (src.sid + 30, (SELECT label FROM mdlk WHERE mdlk.k = src.sid))");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 94",
            "MERGE INTO mdt USING (SELECT 2 sid) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET s = "
                + "(SELECT label FROM mdlk WHERE mdlk.k = mdt.id)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT 2 sid, 'q' tag) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE "
                + "SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid AND src.tag = 'q')");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT 2 sid) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET s = "
                + "(SELECT label FROM mdlk WHERE mdlk.k = sid)");
        assertMerge("0",
            "MERGE INTO mdt USING (SELECT NULL::INT sid) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET"
                + " s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT 2::INT sid) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET s "
                + "= (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1", "MERGE INTO mdt USING (SELECT 2 sid) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET s = "
            + "(SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertAnswer("1|a ;; 2|two ;; 3|c", "SELECT id, s FROM mdt ORDER BY id");
    }

    @Test
    public void theDerivedSourceShapesThatPinAColumn() {
        assertMerge("REFUSED Duplicate row detected during DML action | Row Values: [2, \"b\"]",
            "MERGE INTO mdt USING (SELECT 2 sid FROM mds2) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE "
                + "SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED Duplicate row detected during DML action | Row Values: [2, \"b\"]",
            "MERGE INTO mdt USING (SELECT 2 sid UNION ALL SELECT 2) src ON mdt.id = src.sid WHEN MATCHED THEN"
                + " UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED Duplicate row detected during DML action | Row Values: [2, \"b\"]",
            "MERGE INTO mdt USING (SELECT sid FROM mdss) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET"
                + " s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED Duplicate row detected during DML action | Row Values: [2, \"b\"]",
            "MERGE INTO mdt USING mdss src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET s = (SELECT label"
                + " FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT sid + 1 sid FROM mds1) src ON mdt.id = src.sid WHEN MATCHED THEN "
                + "UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT COUNT(*) sid FROM mds2) src ON mdt.id = src.sid WHEN MATCHED THEN "
                + "UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 111",
            "MERGE INTO mdt USING (SELECT SUM(sid) sid FROM mds2) src ON mdt.id = src.sid WHEN MATCHED THEN "
                + "UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 124",
            "MERGE INTO mdt USING (SELECT MAX(sid) sid FROM mds2 GROUP BY sid) src ON mdt.id = src.sid WHEN "
                + "MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT DISTINCT sid FROM mdss) src ON mdt.id = src.sid WHEN MATCHED THEN "
                + "UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 110",
            "MERGE INTO mdt USING (SELECT sid FROM mds2 LIMIT 1) src ON mdt.id = src.sid WHEN MATCHED THEN "
                + "UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED Duplicate row detected during DML action | Row Values: [2, \"b\"]",
            "MERGE INTO mdt USING (SELECT 2 sid, sid other FROM mds2) src ON mdt.id = src.sid WHEN MATCHED "
                + "THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 115",
            "MERGE INTO mdt USING (SELECT 2 sid, sid other FROM mds2) src ON mdt.id = src.sid WHEN MATCHED "
                + "THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.other)");
        assertMerge("0",
            "MERGE INTO mdt USING (SELECT NULL sid UNION ALL SELECT NULL) src ON mdt.id = src.sid WHEN "
                + "MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 116",
            "MERGE INTO mdt USING (SELECT 1 sid UNION ALL SELECT NULL) src ON mdt.id = src.sid WHEN MATCHED "
                + "THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT LENGTH('ab') sid) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE "
                + "SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT 'two' lab) src ON mdt.id = 2 WHEN MATCHED THEN UPDATE SET s = "
                + "(SELECT MAX(k) FROM mdlk WHERE mdlk.label = src.lab)");
        assertMerge("REFUSED Duplicate row detected during DML action | Row Values: [2, \"b\"]",
            "MERGE INTO mdt USING (SELECT sid lab FROM mdsv) src ON mdt.id = 2 WHEN MATCHED THEN UPDATE SET s"
                + " = (SELECT MAX(k) FROM mdlk WHERE mdlk.label = src.lab)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT 2 sid) AS src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET s = "
                + "(SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt AS tt USING (SELECT 2 sid) src ON tt.id = src.sid WHEN MATCHED THEN UPDATE SET s "
                + "= (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("0",
            "MERGE INTO mdt USING (SELECT 2 sid FROM mds2 WHERE 1 = 0) src ON mdt.id = src.sid WHEN MATCHED "
                + "THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT * FROM (SELECT 2 sid)) src ON mdt.id = src.sid WHEN MATCHED THEN "
                + "UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 94",
            "MERGE INTO mdt USING (SELECT 2 sid) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET s = "
                + "(SELECT label FROM mdlk WHERE mdlk.k = src.sid + mdt.id - mdt.id)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT 2 sid) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET s = "
                + "(SELECT MAX(label) FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED Duplicate row detected during DML action | Row Values: [2, \"b\"]",
            "MERGE INTO mdt USING (SELECT 2 sid FROM mds2) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE "
                + "SET s = (SELECT MAX(label) FROM mdlk WHERE mdlk.k > src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT 2 sid) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET s = "
                + "(SELECT label FROM mdlk WHERE mdlk.k > src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 101",
            "MERGE INTO mdt USING (SELECT RANDOM() sid) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET "
                + "s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 124",
            "MERGE INTO mdt USING (SELECT LENGTH('ab') sid UNION ALL SELECT 2) src ON mdt.id = src.sid WHEN "
                + "MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 137",
            "MERGE INTO mdt USING (SELECT UUID_STRING() sid UNION ALL SELECT UUID_STRING()) src ON mdt.id = "
                + "src.sid WHEN MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED Duplicate row detected during DML action | Row Values: [2, \"b\"]",
            "MERGE INTO mdt USING (SELECT LENGTH('ab') sid FROM mds2) src ON mdt.id = src.sid WHEN MATCHED "
                + "THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("0",
            "MERGE INTO mdt USING (SELECT NULL sid FROM mds2) src ON mdt.id = src.sid WHEN MATCHED THEN "
                + "UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("0",
            "MERGE INTO mdt USING (SELECT sid FROM mdsn) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET"
                + " s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("0",
            "MERGE INTO mdt USING mdsn src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET s = (SELECT label"
                + " FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 111",
            "MERGE INTO mdt USING (SELECT RANDOM() sid FROM mds2) src ON mdt.id = src.sid WHEN MATCHED THEN "
                + "UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT 2 sid WHERE 1 = 1) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE"
                + " SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT a.sid FROM mds1 a JOIN mds1 b ON TRUE) src ON mdt.id = src.sid WHEN"
                + " MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED Duplicate row detected during DML action | Row Values: [2, \"b\"]",
            "MERGE INTO mdt USING (SELECT 2 sid FROM mds2 a JOIN mds2 b ON TRUE) src ON mdt.id = src.sid WHEN"
                + " MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 109",
            "MERGE INTO mdt USING (SELECT 2 sid UNION SELECT 3) src ON mdt.id = src.sid WHEN MATCHED THEN "
                + "UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT 2 sid UNION SELECT 2) src ON mdt.id = src.sid WHEN MATCHED THEN "
                + "UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT 2 sid EXCEPT SELECT 3) src ON mdt.id = src.sid WHEN MATCHED THEN "
                + "UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED Duplicate row detected during DML action | Row Values: [2, \"b\"]",
            "MERGE INTO mdt USING (SELECT sid FROM mds1 UNION ALL SELECT 2) src ON mdt.id = src.sid WHEN "
                + "MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 116",
            "MERGE INTO mdt USING (SELECT NULL sid UNION ALL SELECT 2) src ON mdt.id = src.sid WHEN MATCHED "
                + "THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED Duplicate row detected during DML action | Row Values: [2, \"b\"]",
            "MERGE INTO mdt USING (SELECT ABS(-2) sid UNION ALL SELECT 2 FROM mds2) src ON mdt.id = src.sid "
                + "WHEN MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED Numeric value 'x' is not recognized",
            "MERGE INTO mdt USING (SELECT 'x' sid) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET s = "
                + "(SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT 2 sid ORDER BY 1) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE "
                + "SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT 2 sid LIMIT 1) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET"
                + " s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 147",
            "MERGE INTO mdt USING (SELECT sid FROM mds2 QUALIFY ROW_NUMBER() OVER (ORDER BY sid) = 1) src ON "
                + "mdt.id = src.sid WHEN MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = "
                + "src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT MAX(sid) sid FROM mds2 WHERE sid > 0) src ON mdt.id = src.sid WHEN "
                + "MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT LENGTH('ab') sid FROM mds1) src ON mdt.id = src.sid WHEN MATCHED "
                + "THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED Duplicate row detected during DML action | Row Values: [2, \"b\"]",
            "MERGE INTO mdt USING (SELECT 2 sid FROM TABLE(GENERATOR(ROWCOUNT => 3))) src ON mdt.id = src.sid"
                + " WHEN MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT CURRENT_DATE() d, 2 sid) src ON mdt.id = src.sid WHEN MATCHED THEN "
                + "UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 136",
            "MERGE INTO mdt USING (SELECT seq4() sid FROM TABLE(GENERATOR(ROWCOUNT => 1))) src ON mdt.id = "
                + "src.sid WHEN MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT 2 sid) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET s = "
                + "(SELECT label FROM mdlk WHERE mdlk.k = src.sid OR mdlk.k = 7)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT 2 sid) src ON mdt.id = src.sid WHEN MATCHED THEN DELETE");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT 2 sid, NULL n) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET"
                + " s = (SELECT label FROM mdlk WHERE mdlk.k = src.n)");
    }

    @Test
    public void joinsAggregatesSetOperatorsAndViews() {
        assertMerge("REFUSED Duplicate row detected during DML action | Row Values: [2, \"b\"]",
            "MERGE INTO mdt USING (SELECT a.sid FROM mds1 a JOIN mds2 b ON TRUE) src ON mdt.id = src.sid WHEN"
                + " MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 126",
            "MERGE INTO mdt USING (SELECT b.sid FROM mds1 a JOIN mds2 b ON TRUE) src ON mdt.id = src.sid WHEN"
                + " MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT a.sid FROM mds1 a LEFT JOIN mds2 b ON a.sid = b.sid) src ON mdt.id "
                + "= src.sid WHEN MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 140",
            "MERGE INTO mdt USING (SELECT b.sid FROM mds2 a LEFT JOIN mds1 b ON a.sid = b.sid) src ON mdt.id "
                + "= src.sid WHEN MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED Duplicate row detected during DML action | Row Values: [2, \"b\"]",
            "MERGE INTO mdt USING (SELECT a.sid FROM mds1 a, mds2 b) src ON mdt.id = src.sid WHEN MATCHED "
                + "THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("0",
            "MERGE INTO mdt USING (SELECT a.sid + b.sid sid FROM mds1 a JOIN mds1 b ON TRUE) src ON mdt.id = "
                + "src.sid WHEN MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED Duplicate row detected during DML action | Row Values: [2, \"b\"]",
            "MERGE INTO mdt USING (SELECT sid FROM mds1 JOIN mdlk ON TRUE) src ON mdt.id = src.sid WHEN "
                + "MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 140",
            "MERGE INTO mdt USING (SELECT b.sid FROM mds2 a FULL JOIN mds1 b ON a.sid = b.sid) src ON mdt.id "
                + "= src.sid WHEN MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 125",
            "MERGE INTO mdt USING (SELECT MAX(sid) sid FROM mds2 WHERE sid > 1) src ON mdt.id = src.sid WHEN "
                + "MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 125",
            "MERGE INTO mdt USING (SELECT MAX(sid) sid FROM mds2 WHERE sid = 1) src ON mdt.id = src.sid WHEN "
                + "MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT MIN(sid) sid FROM mds2 WHERE sid < 5) src ON mdt.id = src.sid WHEN "
                + "MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT MAX(sid) + 0 sid FROM mds2) src ON mdt.id = src.sid WHEN MATCHED "
                + "THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 127",
            "MERGE INTO mdt USING (SELECT COUNT(sid) sid FROM mds2 WHERE sid > 1) src ON mdt.id = src.sid "
                + "WHEN MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 111",
            "MERGE INTO mdt USING (SELECT AVG(sid) sid FROM mds2) src ON mdt.id = src.sid WHEN MATCHED THEN "
                + "UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT MAX(sid) sid FROM mds2 GROUP BY ALL) src ON mdt.id = src.sid WHEN "
                + "MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED Duplicate row detected during DML action | Row Values: [2, \"b\"]",
            "MERGE INTO mdt USING (SELECT 2 sid FROM mds2 GROUP BY sid) src ON mdt.id = src.sid WHEN MATCHED "
                + "THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("0",
            "MERGE INTO mdt USING (SELECT 2 sid INTERSECT SELECT 3) src ON mdt.id = src.sid WHEN MATCHED THEN"
                + " UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("0",
            "MERGE INTO mdt USING (SELECT 2 sid MINUS SELECT 2) src ON mdt.id = src.sid WHEN MATCHED THEN "
                + "UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 118",
            "MERGE INTO mdt USING (SELECT sid FROM mds2 EXCEPT SELECT 2) src ON mdt.id = src.sid WHEN MATCHED"
                + " THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("0",
            "MERGE INTO mdt USING (SELECT 2 sid EXCEPT SELECT sid FROM mds2) src ON mdt.id = src.sid WHEN "
                + "MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 121",
            "MERGE INTO mdt USING (SELECT sid FROM mds2 INTERSECT SELECT 2) src ON mdt.id = src.sid WHEN "
                + "MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED Duplicate row detected during DML action | Row Values: [2, \"b\"]",
            "MERGE INTO mdt USING (SELECT 2 sid UNION ALL BY NAME SELECT 2 sid) src ON mdt.id = src.sid WHEN "
                + "MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 135",
            "MERGE INTO mdt USING (SELECT LENGTH('ab') sid UNION ALL SELECT LENGTH('ab')) src ON mdt.id = "
                + "src.sid WHEN MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT SQRT(4) sid) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET s"
                + " = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 134",
            "MERGE INTO mdt USING (SELECT LENGTH('ab') sid FROM mds2 UNION ALL SELECT 2) src ON mdt.id = "
                + "src.sid WHEN MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED Duplicate row detected during DML action | Row Values: [2, \"b\"]",
            "MERGE INTO mdt USING (SELECT ABS(-2) sid UNION ALL SELECT 2) src ON mdt.id = src.sid WHEN "
                + "MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("0",
            "MERGE INTO mdt USING (SELECT NULL sid UNION SELECT NULL) src ON mdt.id = src.sid WHEN MATCHED "
                + "THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | invalid type [CAST(CURRENT_TIMESTAMP() AS NUMBER(38,0))] for "
                + "parameter 'TO_NUMBER'",
            "MERGE INTO mdt USING (SELECT CURRENT_TIMESTAMP()::INT sid) src ON mdt.id = src.sid WHEN MATCHED "
                + "THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT 2) src (sid) ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET s = "
                + "(SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 114",
            "MERGE INTO mdt USING (SELECT sid, 2 FROM mds2) src (o, sid) ON mdt.id = src.sid WHEN MATCHED "
                + "THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 114",
            "MERGE INTO mdt USING (SELECT sid, 2 FROM mds2) src (sid, o) ON mdt.id = src.sid WHEN MATCHED "
                + "THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT 2 sid) ON mdt.id = sid WHEN MATCHED THEN UPDATE SET s = (SELECT "
                + "label FROM mdlk WHERE mdlk.k = sid)");
        assertMerge("1",
            "MERGE INTO mdt USING mdv1 src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET s = (SELECT label"
                + " FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT sid FROM mdv1) src ON mdt.id = src.sid WHEN MATCHED THEN UPDATE SET"
                + " s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
    }

    @Test
    public void aRenamedSourceColumnReadsTheInnerNameFirst() {
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 118",
            "MERGE INTO mdt USING (SELECT sid x, 2 y FROM mds2) src (o, sid) ON mdt.id = src.sid WHEN MATCHED"
                + " THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.o)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 120",
            "MERGE INTO mdt USING (SELECT 2 sid, sid o FROM mds2) src (o, sid) ON mdt.id = src.sid WHEN "
                + "MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.o)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 108",
            "MERGE INTO mdt USING (SELECT sid, 2 FROM mds2) src (o, sid) ON mdt.id = 2 WHEN MATCHED THEN "
                + "UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 108",
            "MERGE INTO mdt USING (SELECT sid, 2 FROM mds2) src (o, sid) ON mdt.id = 2 WHEN MATCHED THEN "
                + "UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.o)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT sid FROM mds1) src (q) ON mdt.id = src.q WHEN MATCHED THEN UPDATE "
                + "SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.q)");
        assertMerge("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 112",
            "MERGE INTO mdt USING (SELECT sid, 2 two FROM mds2) src (o, sid) ON mdt.id = 2 WHEN MATCHED THEN "
                + "UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED Duplicate row detected during DML action | Row Values: [2, \"b\"]",
            "MERGE INTO mdt USING (SELECT 2 sid UNION ALL SELECT 2 FROM mds2) src ON mdt.id = src.sid WHEN "
                + "MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("REFUSED Duplicate row detected during DML action | Row Values: [2, \"b\"]",
            "MERGE INTO mdt USING (SELECT 1 + 1 sid FROM mds2 GROUP BY sid) src ON mdt.id = src.sid WHEN "
                + "MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT MAX(sid) sid FROM mds2 WHERE sid >= 1) src ON mdt.id = src.sid WHEN"
                + " MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
        assertMerge("1",
            "MERGE INTO mdt USING (SELECT MAX(sid) sid FROM mds2 WHERE TRUE) src ON mdt.id = src.sid WHEN "
                + "MATCHED THEN UPDATE SET s = (SELECT label FROM mdlk WHERE mdlk.k = src.sid)");
    }
}
