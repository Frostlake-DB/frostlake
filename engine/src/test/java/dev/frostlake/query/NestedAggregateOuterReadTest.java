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
 * A query nested in a correlated subquery that is, for each outer row, one value of that row — one aggregate row of
 * MIN, MAX or COUNT over a single relation of its own with no filter or grouping, reading the outer row only beside
 * its aggregates — counts its read of the outer row at the level of the subquery around it, so
 * {@code (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k) + o.k FROM one))} is answered in a select list, a
 * WHERE, a HAVING, an IN's query and a DELETE's or an UPDATE's WHERE, where a filter of its own, a join, SUM, AVG, a
 * DISTINCT call, an outer name inside the aggregate or a name of the query between leave it a read from two levels in
 * (live-verified).
 */
public class NestedAggregateOuterReadTest extends MeasuredCellsTestSupport {

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
    public void aNestedAggregateBesideTheOuterRowIsAValue() {
        assertAnswer("1|1 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k) + o.k FROM one)) FROM "
            + "o ORDER BY id");
        assertAnswer("1|1 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MIN(one.k) + o.k FROM one)) FROM "
            + "o ORDER BY id");
        assertAnswer("1|2 ;; 2|1 ;; 3|0 ;; 4|1 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT COUNT(*) + o.k FROM one)) FROM o "
            + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k + o.k) FROM one)) FROM "
            + "o ORDER BY id");
        assertAnswer("1|1 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(nn.k) + o.k FROM nn)) FROM o "
            + "ORDER BY id");
        assertAnswer("1|2 ;; 2|1 ;; 3|0 ;; 4|1 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MIN(nn.k) + o.k FROM nn)) FROM o "
            + "ORDER BY id");
        assertAnswer("1|0 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k > (SELECT MAX(one.k) + o.k FROM one)) FROM "
            + "o ORDER BY id");
        assertAnswer("1|2 ;; 2|2 ;; 3|2 ;; 4|2 ;; 5|2",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT COALESCE(MAX(one.k), o.k) FROM "
            + "one)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k) + o.k FROM one WHERE "
            + "one.val > 0)) FROM o ORDER BY id");
        assertAnswer("1|0 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(e.k) + o.k FROM e)) FROM o "
            + "ORDER BY id");
        assertAnswer("1|1 ;; 2|2 ;; 3|0 ;; 4|2 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT COUNT(*) + o.k FROM e)) FROM o "
            + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 48",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k) + o.k FROM one GROUP "
            + "BY one.val)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 48",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT one.k + o.k - 2 FROM one)) FROM o"
            + " ORDER BY id");
        assertAnswer("1|0 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k) + i.val - 19 FROM "
            + "one)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k) + o.k + i.val - 21 "
            + "FROM one)) FROM o ORDER BY id");
        assertAnswer("1|1 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k IN (SELECT MAX(one.k) + o.k FROM one)) FROM"
            + " o ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, (SELECT SUM(i.val) FROM i WHERE i.k = (SELECT MAX(one.k) + o.k FROM one)) "
            + "FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT i.val FROM i WHERE i.k = (SELECT MAX(one.k) + o.k FROM one)) FROM o "
            + "ORDER BY id");
        assertAnswer("1",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = (SELECT MAX(one.k) + o.k FROM"
            + " one)) ORDER BY id");
        assertAnswer("1",
            "SELECT id FROM o WHERE 0 < (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k) + o.k "
            + "FROM one)) ORDER BY id");
        assertAnswer("",
            "SELECT o.k, COUNT(*) FROM o GROUP BY o.k HAVING COUNT(*) = (SELECT COUNT(*) FROM i WHERE"
            + " i.k = (SELECT MAX(one.k) + o.k - 1 FROM one)) ORDER BY 1");
        assertAnswer("1|8 ;; 2|9 ;; 3|null ;; 4|9 ;; 5|12",
            "SELECT id, (SELECT COUNT(*) + (SELECT MAX(one.k) + o.k FROM one) FROM i) FROM o ORDER BY"
            + " id");
        assertAnswer("1|0 ;; 2|-1 ;; 3|null ;; 4|-1 ;; 5|-4",
            "SELECT id, (SELECT MAX(i.k) - (SELECT MAX(one.k) + o.k FROM one) FROM i) FROM o ORDER BY"
            + " id");
        assertAnswer("1|3 ;; 2|4 ;; 3|null ;; 4|4 ;; 5|7",
            "SELECT id, (SELECT MAX(one.k) + o.k FROM one) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k IN (SELECT nn.k FROM nn WHERE nn.k = "
            + "(SELECT MAX(one.k) + o.k - 2 FROM one))) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(nn.k) FROM nn WHERE nn.k = "
            + "(SELECT MAX(one.k) + o.k - 2 FROM one))) FROM o ORDER BY id");
        assertAnswer("1|2 ;; 2|1 ;; 3|0 ;; 4|1 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(nn.k) + (SELECT MAX(one.k) + "
            + "o.k FROM one) - 3 FROM nn)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE EXISTS (SELECT 1 FROM nn WHERE nn.k"
            + " = i.k AND i.k = (SELECT MAX(one.k) + o.k - 1 FROM one))) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(nn.k) FROM nn HAVING "
            + "MAX(nn.k) = (SELECT MAX(one.k) + o.k - 1 FROM one))) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(val) + o.id FROM i i3 WHERE "
            + "i3.k = o.k)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k) FROM one WHERE one.k ="
            + " o.k)) FROM o ORDER BY id");
    }

    @Test
    public void itsAggregatesRelationAndFilter() {
        engine.execute("CREATE OR REPLACE VIEW onev AS SELECT k, val FROM one");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT SUM(one.k) + o.k FROM one)) FROM "
            + "o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT AVG(one.k) + o.k FROM one)) FROM "
            + "o ORDER BY id");
        assertAnswer("1|2 ;; 2|1 ;; 3|0 ;; 4|1 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT COUNT(one.k) + o.k FROM one)) "
            + "FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT COUNT(DISTINCT one.k) + o.k FROM "
            + "one)) FROM o ORDER BY id");
        assertAnswer("1|1 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k + 0) + o.k FROM one)) "
            + "FROM o ORDER BY id");
        assertAnswer("1|1 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(onev.k) + o.k FROM onev)) "
            + "FROM o ORDER BY id");
        assertAnswer("1|1 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(d.k) + o.k FROM (SELECT k "
            + "FROM one) d)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k) + o.k FROM one WHERE "
            + "TRUE)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k) + o.k FROM one WHERE "
            + "one.k > 0)) FROM o ORDER BY id");
        assertAnswer("1|1 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT COALESCE(MAX(one.k), 0) + o.k "
            + "FROM one)) FROM o ORDER BY id");
        assertAnswer("1|1 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT o.k + MAX(one.k) FROM one)) FROM "
            + "o ORDER BY id");
        assertAnswer("1|1 ;; 2|2 ;; 3|0 ;; 4|2 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k) - MIN(one.k) + o.k "
            + "FROM one)) FROM o ORDER BY id");
        assertAnswer("1|2 ;; 2|1 ;; 3|0 ;; 4|1 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(i2.k) + o.k - 2 FROM i i2)) "
            + "FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k) + o.k FROM one, nn)) "
            + "FROM o ORDER BY id");
        assertAnswer("1|2 ;; 2|1 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k) + o.id - 1 FROM one)) "
            + "FROM o ORDER BY id");
        assertAnswer("1|1 ;; 2|2 ;; 3|1 ;; 4|2 ;; 5|2",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT IFF(o.k > 1, MAX(one.k), 1) FROM "
            + "one)) FROM o ORDER BY id");
        assertAnswer("1|1 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k) + o.k FROM one LIMIT "
            + "1)) FROM o ORDER BY id");
        assertAnswer("1|1 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k) + o.k FROM one ORDER "
            + "BY 1)) FROM o ORDER BY id");
        assertAnswer("1|1 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT 2 + o.k)) FROM o ORDER BY id");
        assertAnswer("",
            "SELECT o.k, COUNT(*) FROM o GROUP BY o.k HAVING COUNT(*) = (SELECT COUNT(*) FROM i WHERE"
            + " i.k = 1 + o.k) ORDER BY 1");
        assertAnswer("1|8 ;; 2|9 ;; 3|null ;; 4|9 ;; 5|12",
            "SELECT id, (SELECT COUNT(*) + 2 + o.k FROM i) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE EXISTS (SELECT 1 FROM nn WHERE nn.k"
            + " = i.k AND i.k = o.k + 1)) ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, (SELECT SUM(i.val) FROM i WHERE i.k = 2 + o.k) FROM o ORDER BY id");
        engine.execute("UPDATE o SET k = k WHERE k = (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k) + "
            + "o.k FROM one))");
        engine.execute("DELETE FROM o WHERE 5 < (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k) + o.k "
            + "FROM one))");
    }

    @Test
    public void neighboursOfTheLift() {
        assertAnswer("1|3 ;; 2|3 ;; 3|3 ;; 4|3 ;; 5|3",
            "SELECT id, (SELECT COUNT(*) FROM i JOIN nn ON nn.k = i.k WHERE o.id NOT IN (SELECT val "
            + "FROM i i2 WHERE i2.k = o.k)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id NOT IN (SELECT val FROM i i2 WHERE i2.k = "
            + "o.k) OR i.k = 1) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE (o.id IN (SELECT val FROM i i2 WHERE i2.k = "
            + "o.k)) = FALSE) FROM o ORDER BY id");
        assertAnswer("1|2 ;; 2|1 ;; 3|1 ;; 4|1 ;; 5|1",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k) + o.k FROM one) OR i.k"
            + " = 1) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|0 ;; 4|5 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i JOIN nn ON nn.k = (SELECT MAX(one.k) + o.k - 2 FROM "
            + "one)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE (SELECT MAX(one.k) + o.k - 2 FROM one) IN "
            + "(SELECT k FROM nn)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 5|5 ;; null|5",
            "SELECT o.k, (SELECT COUNT(*) FROM i WHERE o.k NOT IN (SELECT val FROM i i2 WHERE i2.k = "
            + "o.k)) FROM o GROUP BY o.k ORDER BY 1");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id NOT IN (SELECT val FROM i i2 WHERE i2.k = "
            + "o.k GROUP BY val)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id NOT IN (SELECT val FROM i i2 WHERE i2.k = "
            + "o.k LIMIT 1)) FROM o ORDER BY id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id NOT IN (SELECT DISTINCT val FROM i i2 "
            + "WHERE i2.k = o.k)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE NOT EXISTS (SELECT 1 FROM i i2 WHERE i2.k > "
            + "o.k)) FROM o ORDER BY id");
        assertAnswer("1|0 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE o.id IN (SELECT val FROM i i2 WHERE i2.k = o.k "
            + "OR i2.k IS NULL)) FROM o ORDER BY id");
        assertAnswer("1|1 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k) + o.k FROM one) AND "
            + "o.id NOT IN (SELECT val FROM i i2 WHERE i2.k = o.k)) FROM o ORDER BY id");
        assertAnswer("1|1 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k) FROM one) + o.k) FROM "
            + "o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT ANY_VALUE(one.k) + o.k FROM one))"
            + " FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k) + o.k FROM one HAVING "
            + "COUNT(*) > 0)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 48",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k) + o.k FROM one LIMIT "
            + "0)) FROM o ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o");
        engine.execute("UPDATE t SET k = -1 WHERE EXISTS (SELECT 1 FROM i WHERE i.k = t.k AND t.id IN (SELECT "
            + "val FROM i i2 WHERE i2.k = t.k))");
        assertAnswer("1|1 ;; 2|2 ;; 3|null ;; 4|2 ;; 5|5",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("UPDATE t SET k = (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(one.k) + t.k FROM one))");
        assertAnswer("1|1 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, k FROM t ORDER BY id");
        engine.execute("MERGE INTO t USING o s ON t.id = s.id WHEN MATCHED THEN UPDATE SET k = (SELECT COUNT(*) "
            + "FROM i WHERE i.k = (SELECT MAX(one.k) + s.k FROM one))");
        assertAnswer("1|1 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0",
            "SELECT id, k FROM t ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 34",
            "UPDATE t SET k = IFF(t.id NOT IN (SELECT val FROM i i2 WHERE i2.k = t.k), 1, 0) WHERE "
            + "t.id NOT IN (SELECT k FROM nn)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 34",
            "UPDATE t SET k = IFF(t.id NOT IN (SELECT val FROM i i2 WHERE i2.k = t.k GROUP BY val), "
            + "1, 0)");
    }
}
