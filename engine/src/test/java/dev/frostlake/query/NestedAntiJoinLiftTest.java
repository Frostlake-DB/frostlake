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
 * An anti-join nested in a correlated subquery that reads the outer row alone — a NOT IN or a comparison with ALL of
 * the outer row's bare columns over a select of its own correlated with the outer row — is lifted out of the
 * subquery, so the subquery is answered in a select list, a WHERE's scalar, a HAVING and as an UPDATE's or a
 * MERGE's SET value; inside an EXISTS or an IN of a WHERE, a DELETE's and an UPDATE's too, it is refused at the
 * anti-join's own query. A nested membership or EXISTS the statistics prove FALSE is folded away, and an UPDATE's or
 * a MERGE's SET value is refused for a keyed anti-join over a query that may answer NULL (live-verified).
 */
public class NestedAntiJoinLiftTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void createTables() {
        engine.execute("CREATE OR REPLACE TABLE o (id INT, k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts"
            + " TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, a ARRAY, ob OBJECT)");
        engine.execute("INSERT INTO o SELECT 1, 1, 1.50, 1.5, 'a', '2020-01-01'::DATE, '2020-01-01 "
            + "10:00:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(1), ARRAY_CONSTRUCT(1,2), "
            + "OBJECT_CONSTRUCT('k',1) UNION ALL SELECT 2, 2, 2.00, 2.0, 'B', '2020-01-02'::DATE, "
            + "'2020-01-02 00:00:00'::TIMESTAMP_NTZ, FALSE, TO_VARIANT('x'), ARRAY_CONSTRUCT(2), "
            + "OBJECT_CONSTRUCT('k',2) UNION ALL SELECT 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, "
            + "NULL, NULL, NULL UNION ALL SELECT 4, 2, 2.00, 2.0, 'b', '2020-01-03'::DATE, '2020-01-03 "
            + "12:30:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(2), ARRAY_CONSTRUCT(3,NULL), "
            + "OBJECT_CONSTRUCT() UNION ALL SELECT 5, 5, 5.25, 5.25, 'e', '2020-01-05'::DATE, "
            + "'2020-01-05 00:00:00'::TIMESTAMP_NTZ, NULL, PARSE_JSON('null'), ARRAY_CONSTRUCT(), "
            + "OBJECT_CONSTRUCT('z',NULL)");
        engine.execute("CREATE OR REPLACE TABLE i (k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, val INT)");
        engine.execute("INSERT INTO i SELECT 1, 1.50, 1.5, 'A', '2020-01-01'::DATE, '2020-01-01 "
            + "10:00:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(1), 10 UNION ALL SELECT 2, 2.00, 2.0, 'b', "
            + "'2020-01-02'::DATE, '2020-01-02 00:00:00'::TIMESTAMP_NTZ, FALSE, TO_VARIANT('x'), 20 "
            + "UNION ALL SELECT 2, 2.10, 2.1, 'b', '2020-01-04'::DATE, '2020-01-04 "
            + "00:00:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(2), 21 UNION ALL SELECT NULL, NULL, NULL, "
            + "NULL, NULL, NULL, NULL, NULL, 30 UNION ALL SELECT 3, 3.00, 3.0, 'c', '2020-01-03'::DATE,"
            + " '2020-01-03 12:30:00'::TIMESTAMP_NTZ, NULL, TO_VARIANT(3), NULL");
        engine.execute("CREATE OR REPLACE TABLE e (k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, val INT)");
        engine.execute("CREATE OR REPLACE TABLE one (k INT, val INT)");
        engine.execute("INSERT INTO one VALUES (2, 7)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1), (NULL)");
    }

    @Test
    public void anUpdateReadsALiftedAntiJoin() {
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("UPDATE t SET k = (SELECT COUNT(*) FROM i WHERE t.id NOT IN (SELECT val FROM i i2 WHERE "
            + "i2.k = t.k))");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("UPDATE t SET k = (SELECT COUNT(*) FROM i WHERE t.id <> ALL (SELECT val FROM i i2 WHERE "
            + "i2.k = t.k))");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 18",
            "UPDATE t SET k = (SELECT COUNT(*) FROM i WHERE i.val NOT IN (SELECT val FROM i i2 WHERE "
            + "i2.k = t.k))");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("UPDATE t SET k = (SELECT COUNT(*) FROM i WHERE t.id NOT IN (SELECT k FROM nn WHERE nn.k "
            + "= t.k))");
        assertAnswer("1|0 ;; 2|0 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("UPDATE t SET k = (SELECT MAX(i.val) FROM i WHERE t.id NOT IN (SELECT val FROM i i2 WHERE"
            + " i2.k = t.k))");
        assertAnswer("1|30 ;; 2|30 ;; 3|30 ;; 4|30 ;; 5|30",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("UPDATE t SET k = (SELECT COUNT(*) FROM i WHERE i.k = t.k AND t.id NOT IN (SELECT val "
            + "FROM i i2 WHERE i2.k = t.k))");
        assertAnswer("1|1 ;; 2|2 ;; 3|0 ;; 4|2 ;; 5|0",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("UPDATE t SET k = (SELECT COUNT(*) FROM i WHERE t.id IN (SELECT val FROM i i2 WHERE i2.k "
            + "= t.k))");
        assertAnswer("1|0 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("UPDATE t SET k = (SELECT COUNT(*) FROM i WHERE NOT EXISTS (SELECT 1 FROM i i2 WHERE i2.k"
            + " = t.k AND i2.val = t.id))");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 40",
            "UPDATE t SET k = CASE WHEN t.id NOT IN (SELECT val FROM i i2 WHERE i2.k = t.k) THEN 1 "
            + "ELSE 0 END");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 39",
            "UPDATE t SET k = -1 WHERE t.id NOT IN (SELECT val FROM i i2 WHERE i2.k = t.k)");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 83",
            "UPDATE t SET k = -1 WHERE EXISTS (SELECT 1 FROM i WHERE i.k = t.k AND t.id NOT IN "
            + "(SELECT val FROM i i2 WHERE i2.k = t.k))");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 78",
            "UPDATE t SET k = -1 WHERE EXISTS (SELECT 1 FROM i WHERE i.k = t.k AND t.id <> ALL "
            + "(SELECT val FROM i i2 WHERE i2.k = t.k))");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 69",
            "UPDATE t SET k = -1 WHERE EXISTS (SELECT 1 FROM i WHERE t.id NOT IN (SELECT val FROM i "
            + "i2 WHERE i2.k = t.k))");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("UPDATE t SET k = -1 WHERE 0 < (SELECT COUNT(*) FROM i WHERE t.id NOT IN (SELECT val FROM"
            + " i i2 WHERE i2.k = t.k))");
        assertAnswer("1|-1 ;; 2|-1 ;; 3|-1 ;; 4|-1 ;; 5|-1",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 71",
            "UPDATE t SET k = -1 WHERE t.k IN (SELECT i.k FROM i WHERE t.id NOT IN (SELECT val FROM i"
            + " i2 WHERE i2.k = t.k))");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated",
            "UPDATE t SET k = -1 WHERE EXISTS (SELECT 1 FROM i WHERE i.k = t.k AND i.val NOT IN "
            + "(SELECT val FROM i i2 WHERE i2.k = t.k))");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 87",
            "UPDATE t SET k = -1 WHERE NOT EXISTS (SELECT 1 FROM i WHERE i.k = t.k AND t.id NOT IN "
            + "(SELECT val FROM i i2 WHERE i2.k = t.k))");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 83",
            "UPDATE t SET k = -1 WHERE EXISTS (SELECT 1 FROM i WHERE i.k = t.k AND t.id NOT IN "
            + "(SELECT val FROM i i2 WHERE i2.k = i.k))");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 77",
            "DELETE FROM t WHERE EXISTS (SELECT 1 FROM i WHERE i.k = t.k AND t.id NOT IN (SELECT val "
            + "FROM i i2 WHERE i2.k = t.k))");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("DELETE FROM t WHERE 0 < (SELECT COUNT(*) FROM i WHERE t.id NOT IN (SELECT val FROM i i2 "
            + "WHERE i2.k = t.k))");
        assertAnswer("",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 72",
            "DELETE FROM t WHERE EXISTS (SELECT 1 FROM i WHERE i.k = t.k AND t.id <> ALL (SELECT val "
            + "FROM i i2 WHERE i2.k = t.k))");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("MERGE INTO t USING o s ON t.id = s.id WHEN MATCHED THEN UPDATE SET k = (SELECT COUNT(*) "
            + "FROM i WHERE t.id NOT IN (SELECT val FROM i i2 WHERE i2.k = t.k))");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("MERGE INTO t USING o s ON t.id = s.id WHEN MATCHED AND EXISTS (SELECT 1 FROM i WHERE i.k"
            + " = t.k AND t.id NOT IN (SELECT val FROM i i2 WHERE i2.k = t.k)) THEN UPDATE SET k = -1");
        assertAnswer("1|-1 ;; 2|-1 ;; 3|null ;; 4|-1 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("MERGE INTO t USING o s ON t.id = s.id WHEN MATCHED THEN UPDATE SET k = (SELECT COUNT(*) "
            + "FROM i WHERE s.id NOT IN (SELECT val FROM i i2 WHERE i2.k = s.k))");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("MERGE INTO t USING o s ON t.id = s.id WHEN MATCHED AND EXISTS (SELECT 1 FROM i WHERE i.k"
            + " = s.k AND s.id NOT IN (SELECT val FROM i i2 WHERE i2.k = s.k)) THEN DELETE");
        assertAnswer("3|null ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("UPDATE t SET k = s.c FROM (SELECT id, (SELECT COUNT(*) FROM i WHERE o.id NOT IN (SELECT "
            + "val FROM i i2 WHERE i2.k = o.k)) c FROM o) s WHERE s.id = t.id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("UPDATE t SET k = (SELECT COUNT(*) FROM i WHERE t.id NOT IN (SELECT val FROM i i2 WHERE "
            + "i2.k = t.k)) WHERE t.id > 1");
        assertAnswer("1|1 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id NOT IN (SELECT val FROM i i2 WHERE i2.k = "
            + "o.k)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id <> ALL (SELECT val FROM i i2 WHERE i2.k = "
            + "o.k)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 80",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND o.id NOT IN (SELECT "
            + "val FROM i i2 WHERE i2.k = o.k)) ORDER BY id");
    }

    @Test
    public void aSelectReadsALiftedAntiJoin() {
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.k = (SELECT MAX(one.k) FROM one WHERE one.val"
            + " = o.id + 6)) FROM o ORDER BY id");
        assertAnswer("1|30 ;; 2|30 ;; 3|30 ;; 4|30 ;; 5|30",
            "SELECT id, (SELECT MAX(i.val) FROM i WHERE o.id NOT IN (SELECT val FROM i i2 WHERE i2.k "
            + "= o.k)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT i.val FROM i WHERE i.k = 3 AND o.id NOT IN (SELECT val FROM i i2 "
            + "WHERE i2.k = o.k)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i HAVING o.id NOT IN (SELECT val FROM i i2 WHERE i2.k ="
            + " o.k)) FROM o ORDER BY id");
        assertAnswer("1|6 ;; 2|6 ;; 3|6 ;; 4|6 ;; 5|6",
            "SELECT id, (SELECT COUNT(*) + IFF(o.id NOT IN (SELECT val FROM i i2 WHERE i2.k = o.k), "
            + "1, 0) FROM i) FROM o ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM o WHERE 0 < (SELECT COUNT(*) FROM i WHERE o.id NOT IN (SELECT val FROM i "
            + "i2 WHERE i2.k = o.k)) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 68",
            "SELECT id FROM o WHERE o.k IN (SELECT i.k FROM i WHERE o.id NOT IN (SELECT val FROM i i2"
            + " WHERE i2.k = o.k)) ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|true ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND o.id NOT IN (SELECT val FROM i i2"
            + " WHERE i2.k = o.k)) FROM o ORDER BY id");
        assertAnswer("1|0 ;; 2|0 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id NOT IN (SELECT k FROM nn WHERE nn.k = "
            + "o.k)) FROM o ORDER BY id");
        assertAnswer("1|0 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id IN (SELECT val FROM i i2 WHERE i2.k = "
            + "o.k)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE NOT EXISTS (SELECT 1 FROM i i2 WHERE i2.k = o.k"
            + " AND i2.val = o.id)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.k + 1 IN (SELECT k FROM nn WHERE nn.k > o.id "
            + "- 2)) FROM o ORDER BY id");
        assertAnswer("1|1 ;; 2|2 ;; 3|0 ;; 4|2 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = o.k AND o.id NOT IN (SELECT val FROM i i2"
            + " WHERE i2.k = o.k)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id <> ALL (SELECT val FROM i i2 WHERE i2.k = "
            + "o.k)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE NOT (o.id IN (SELECT val FROM i i2 WHERE i2.k ="
            + " o.k))) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 66",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.id NOT IN (SELECT val FROM i i2 "
            + "WHERE i2.k = o.k)) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 75",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND o.id <> ALL (SELECT "
            + "val FROM i i2 WHERE i2.k = o.k)) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 80",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND o.id NOT IN (SELECT "
            + "val FROM i i2 WHERE i2.k = i.k)) ORDER BY id");
        assertAnswer("",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND o.id IN (SELECT val "
            + "FROM i i2 WHERE i2.k = o.k)) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 84",
            "SELECT id FROM o WHERE NOT EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND o.id NOT IN "
            + "(SELECT val FROM i i2 WHERE i2.k = o.k)) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 80",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND o.id NOT IN (SELECT k"
            + " FROM nn WHERE nn.k = o.k)) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND i.val NOT IN (SELECT "
            + "val FROM i i2 WHERE i2.k = o.k)) ORDER BY id");
    }

    @Test
    public void setValuesRefuseAKeyedAntiJoin() {
        engine.execute("CREATE OR REPLACE TABLE t1 AS SELECT id, k FROM o WHERE id = 2");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 40",
            "UPDATE t SET k = CASE WHEN t.id NOT IN (SELECT val FROM i i2 WHERE i2.k = t.k) THEN 1 "
            + "ELSE 0 END");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("UPDATE t SET k = CASE WHEN t.id NOT IN (SELECT k FROM nn WHERE nn.k = t.k) THEN 1 ELSE 0"
            + " END");
        assertAnswer("1|0 ;; 2|0 ;; 3|1 ;; 4|1 ;; 5|1",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 29",
            "UPDATE t SET k = IFF(t.id <> ALL (SELECT val FROM i i2 WHERE i2.k = t.k), 1, 0)");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("UPDATE t SET k = IFF(t.id IN (SELECT val FROM i i2 WHERE i2.k = t.k), 1, 0)");
        assertAnswer("1|0 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("UPDATE t SET k = IFF(NOT EXISTS (SELECT 1 FROM i i2 WHERE i2.k = t.k AND i2.val = t.id),"
            + " 1, 0)");
        assertAnswer("1|1 ;; 2|1 ;; 3|1 ;; 4|1 ;; 5|1",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 40",
            "UPDATE t SET k = CASE WHEN t.id NOT IN (SELECT val FROM i i2 WHERE i2.k = t.k) THEN 1 "
            + "ELSE 0 END WHERE t.id = 2");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 94",
            "MERGE INTO t USING o s ON t.id = s.id WHEN MATCHED THEN UPDATE SET k = CASE WHEN t.id "
            + "NOT IN (SELECT val FROM i i2 WHERE i2.k = t.k) THEN 1 ELSE 0 END");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 34",
            "UPDATE t SET k = IFF(t.id NOT IN (SELECT val FROM i i2 WHERE i2.k = t.k AND i2.val IS "
            + "NOT NULL), 1, 0)");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("UPDATE t SET k = (SELECT COUNT(*) FROM i WHERE t.id NOT IN (SELECT val FROM i i2 WHERE "
            + "i2.k = t.k AND i2.val IS NOT NULL))");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 42",
            "UPDATE t1 SET k = CASE WHEN t1.id NOT IN (SELECT val FROM i i2 WHERE i2.k = t1.k) THEN 1"
            + " ELSE 0 END");
        assertAnswer("2|2",
            "SELECT id, k FROM t1");
        assertAnswer("1|1 ;; 2|1 ;; 3|1 ;; 4|1 ;; 5|1",
            "SELECT id, CASE WHEN o.id NOT IN (SELECT val FROM i i2 WHERE i2.k = o.k) THEN 1 ELSE 0 "
            + "END FROM o ORDER BY id");
        assertAnswer("1|1 ;; 2|1 ;; 3|1 ;; 4|1 ;; 5|1",
            "SELECT id, IFF(o.id <> ALL (SELECT val FROM i i2 WHERE i2.k = o.k), 1, 0) FROM o ORDER "
            + "BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id NOT IN (SELECT val FROM i i2 WHERE i2.k = "
            + "o.k AND i2.val IS NOT NULL)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id NOT IN (SELECT val FROM i i2 WHERE i2.k = "
            + "o.k AND i2.val > 0)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id NOT IN (SELECT val + 0 FROM i i2 WHERE "
            + "i2.k = o.k)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id NOT IN (SELECT val FROM i i2 WHERE i2.k = "
            + "o.k + 0)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id + 1 NOT IN (SELECT val FROM i i2 WHERE "
            + "i2.k = o.k)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id NOT IN (SELECT val FROM i i2 WHERE i2.k = "
            + "o.k AND i2.val <> o.id)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 54",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id NOT IN (SELECT val FROM i i2 WHERE i2.k = "
            + "o.k UNION ALL SELECT 1)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id NOT IN (SELECT i2.val FROM i i2 JOIN nn ON"
            + " nn.k = i2.k WHERE i2.k = o.k)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|0 ;; 4|5 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id NOT IN (SELECT MAX(val) FROM i i2 WHERE "
            + "i2.k = o.k)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id < ALL (SELECT val FROM i i2 WHERE i2.k = "
            + "o.k)) FROM o ORDER BY id");
        assertAnswer("1|0 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id = ANY (SELECT val FROM i i2 WHERE i2.k = "
            + "o.k)) FROM o ORDER BY id");
        assertAnswer("1|0 ;; 2|0 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i i0 WHERE o.id NOT IN (SELECT val FROM i i2 WHERE i2.k"
            + " = o.k) AND o.k NOT IN (SELECT k FROM nn WHERE nn.k = o.id)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE (o.id, o.k) NOT IN (SELECT val, k FROM i i2 "
            + "WHERE i2.k = o.k)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = 2 AND (SELECT COUNT(*) FROM nn WHERE o.id"
            + " NOT IN (SELECT val FROM i i2 WHERE i2.k = o.k)) > 0) FROM o ORDER BY id");
    }

    @Test
    public void whatALiftedAntiJoinReads() {
        assertAnswer("1|0 ;; 2|0 ;; 3|5 ;; 4|0 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id NOT IN (SELECT val FROM i i2 WHERE i2.k > "
            + "o.k)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE NOT EXISTS (SELECT 1 FROM i i2 WHERE i2.k = o.k"
            + " AND i2.val > o.id)) FROM o ORDER BY id");
        assertAnswer("1|0 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id IN (SELECT val FROM i i2 WHERE i2.k >= "
            + "o.k)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE NOT EXISTS (SELECT 1 FROM i i2 WHERE i2.k = o.k"
            + " + 1)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|0 ;; 3|5 ;; 4|0 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id NOT IN (SELECT val FROM i i2 WHERE o.k = "
            + "2)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id NOT IN (SELECT val FROM i i2 WHERE i2.k + "
            + "0 = o.k)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 54",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id NOT IN (SELECT val + o.k FROM i i2 WHERE "
            + "i2.k = o.k)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE NOT EXISTS (SELECT 1 FROM i i2 WHERE i2.k = o.k"
            + " OR i2.val = o.id)) FROM o ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("UPDATE t SET k = IFF(t.id NOT IN (SELECT MAX(val) FROM i i2 WHERE i2.k = t.k), 1, 0)");
        assertAnswer("1|1 ;; 2|1 ;; 3|0 ;; 4|1 ;; 5|0",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("UPDATE t SET k = IFF(t.id NOT IN (SELECT val FROM i i2 WHERE i2.k > t.k), 1, 0)");
        assertAnswer("1|0 ;; 2|0 ;; 3|1 ;; 4|0 ;; 5|1",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("UPDATE t SET k = IFF(t.id NOT IN (SELECT val FROM i i2 WHERE i2.k = t.k AND i2.val < "
            + "t.id * 10), 1, 0)");
        assertAnswer("1|1 ;; 2|1 ;; 3|1 ;; 4|1 ;; 5|1",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("UPDATE t SET k = IFF(NOT (t.id IN (SELECT val FROM i i2 WHERE i2.k = t.k)), 1, 0)");
        assertAnswer("1|1 ;; 2|1 ;; 3|1 ;; 4|1 ;; 5|1",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 34",
            "UPDATE t SET k = IFF(t.id NOT IN (SELECT val FROM i i2 WHERE t.k = 2), 1, 0)");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 33",
            "UPDATE t SET k = IFF(t.k NOT IN (SELECT k FROM i i2 WHERE i2.val = t.id * 10), 1, 0)");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 38",
            "UPDATE t SET k = IFF(t.id + 0 NOT IN (SELECT val FROM i i2 WHERE i2.k = t.k), 1, 0)");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 34",
            "UPDATE t SET k = IFF(t.id NOT IN (SELECT val FROM i i2 WHERE i2.k = t.k GROUP BY val "
            + "HAVING COUNT(*) > 0), 1, 0)");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
    }

    @Test
    public void settledMembershipsFoldAway() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE, 'a'), (7, FALSE, 'b')");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
        engine.execute("INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        engine.execute("CREATE OR REPLACE TABLE h (k INT, w INT)");
        engine.execute("INSERT INTO h VALUES (5, 500), (7, 700)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT MAX(v) FROM g WHERE NOT EXISTS (SELECT 1 FROM h WHERE h.k = fz.id)) "
            + "FROM fz ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM g WHERE NOT EXISTS (SELECT 1 FROM h WHERE h.k = fz.id))"
            + " FROM fz ORDER BY id");
        assertAnswer("5|60 ;; 7|60",
            "SELECT id, (SELECT MAX(v) FROM g WHERE NOT EXISTS (SELECT 1 FROM h WHERE h.k = fz.id AND"
            + " h.w = fz.id)) FROM fz ORDER BY id");
        assertAnswer("5|2 ;; 7|2",
            "SELECT id, (SELECT COUNT(*) FROM g WHERE NOT EXISTS (SELECT 1 FROM h WHERE h.k = fz.id "
            + "AND h.w = fz.id)) FROM fz ORDER BY id");
        assertAnswer("5|null ;; 7|null",
            "SELECT id, (SELECT MAX(v) FROM g WHERE fz.id NOT IN (SELECT h.k FROM h WHERE h.w = fz.id"
            + " * 100)) FROM fz ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT MAX(v) FROM g WHERE fz.id IN (SELECT h.k FROM h WHERE h.w = fz.id * "
            + "100)) FROM fz ORDER BY id");
        assertAnswer("1|30 ;; 2|30 ;; 3|30 ;; 4|30 ;; 5|30",
            "SELECT id, (SELECT MAX(i.val) FROM i WHERE NOT EXISTS (SELECT 1 FROM i i2 WHERE i2.k = "
            + "o.k AND i2.val = o.id)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE NOT EXISTS (SELECT 1 FROM i i2 WHERE i2.k = "
            + "o.k)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT MAX(i.val) FROM i WHERE NOT EXISTS (SELECT 1 FROM i i2 WHERE i2.k = "
            + "o.k)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE NOT EXISTS (SELECT 1 FROM one WHERE one.k = "
            + "o.k)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE NOT EXISTS (SELECT 1 FROM one WHERE one.k = o.k"
            + " AND one.val = o.id)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE NOT EXISTS (SELECT 1 FROM i i2 WHERE i2.val = "
            + "o.id)) FROM o ORDER BY id");
    }

    @Test
    public void quantifiersAndFoldedMemberships() {
        assertAnswer("1|0 ;; 2|0 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id <> ALL (SELECT k FROM nn WHERE nn.k = "
            + "o.k)) FROM o ORDER BY id");
        assertAnswer("1|0 ;; 2|0 ;; 3|5 ;; 4|0 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id < ALL (SELECT k FROM nn WHERE nn.k = o.k))"
            + " FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id = ANY (SELECT k FROM nn WHERE nn.k = o.k))"
            + " FROM o ORDER BY id");
        assertAnswer("1|0 ;; 2|0 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE (o.id, o.k) NOT IN (SELECT k, k FROM nn WHERE "
            + "nn.k = o.k)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.k IN (SELECT k FROM nn WHERE nn.k = o.id)) "
            + "FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE NOT (o.k IN (SELECT k FROM nn WHERE nn.k = "
            + "o.id))) FROM o ORDER BY id");
        assertAnswer("1|0 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE EXISTS (SELECT 1 FROM i i2 WHERE i2.val = "
            + "o.id)) FROM o ORDER BY id");
        assertAnswer("1|0 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(k) FROM i i2 WHERE i2.val = "
            + "o.id)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND o.k IN (SELECT k FROM"
            + " nn WHERE nn.k = o.id)) ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE NOT EXISTS (SELECT 1 FROM i i2 WHERE i2.val = "
            + "o.id + 100)) FROM o ORDER BY id");
        assertAnswer("1|0 ;; 2|0 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.k NOT IN (SELECT k FROM nn WHERE nn.k = "
            + "o.id)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.k NOT IN (SELECT val FROM i i2 WHERE i2.k = "
            + "o.k)) FROM o ORDER BY id");
        assertAnswer("1|0 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id IN (SELECT val FROM i i2 WHERE i2.k = "
            + "o.k)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE NOT EXISTS (SELECT 1 FROM wn WHERE wn.k = o.id "
            + "+ 10)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE NOT EXISTS (SELECT 1 FROM nn WHERE nn.k = o.id "
            + "+ 10)) FROM o ORDER BY id");
    }
}
