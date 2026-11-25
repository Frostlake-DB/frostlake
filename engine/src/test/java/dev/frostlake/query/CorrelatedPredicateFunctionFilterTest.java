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

import org.junit.jupiter.api.Test;

/**
 * A correlated filter reading the outer row through a predicate function (live-verified): EQUAL_NULL, CONTAINS,
 * STARTSWITH, ENDSWITH, LIKE of two or three arguments, ILIKE, RLIKE and REGEXP_LIKE of two, ARRAY_CONTAINS and
 * ARRAYS_OVERLAP are answered as their operator forms are, each argument kept to one side and the outer row read in one
 * of them at most, over a table, a VALUES list or constant UNION ALL rows, beside equality keys or alone. A call mixing
 * the sides in one argument, reading the outer row in two, any other call over the outer row (a call of one argument,
 * IFNULL, IFF, BOOLAND) and REGEXP_LIKE, RLIKE or ILIKE with a further argument stay refused.
 */
public class CorrelatedPredicateFunctionFilterTest extends MeasuredCellsTestSupport {

    private void tables() {
        engine.execute("CREATE TABLE f (ckey VARCHAR, finding_ids ARRAY)");
        engine.execute("INSERT INTO f SELECT 'c1', ARRAY_CONSTRUCT('a', 'b')");
        engine.execute("CREATE TABLE e (fid VARCHAR)");
        engine.execute("INSERT INTO e VALUES ('a'), ('b'), ('z')");
        engine.execute("CREATE TABLE o (id INT, a VARCHAR, n INT, b BOOLEAN, arr ARRAY)");
        engine.execute("INSERT INTO o SELECT 1, 'a', 1, TRUE, ARRAY_CONSTRUCT('a') UNION ALL SELECT 2, 'b', 2, "
            + "FALSE, ARRAY_CONSTRUCT('x', 'b') UNION ALL SELECT 3, NULL, 3, NULL, NULL UNION ALL "
            + "SELECT 4, 'z', NULL, TRUE, ARRAY_CONSTRUCT()");
        engine.execute("CREATE TABLE i (id INT, x VARCHAR, n INT, arr ARRAY, s VARCHAR)");
        engine.execute("INSERT INTO i SELECT 1, 'a', 1, ARRAY_CONSTRUCT('a', 'b'), 'abc' UNION ALL SELECT 2, "
            + "'b', 2, ARRAY_CONSTRUCT('b'), 'bcd' UNION ALL SELECT 3, NULL, NULL, NULL, NULL UNION ALL"
            + " SELECT 4, 'a', 3, ARRAY_CONSTRUCT('c'), 'a%x' UNION ALL SELECT 5, 'c', 5, "
            + "ARRAY_CONSTRUCT(), 'zz'");
        engine.execute("CREATE TABLE ee (fid VARCHAR, cid VARCHAR, k INT, ids ARRAY, o OBJECT)");
        engine.execute("INSERT INTO ee SELECT 'a', 'c1', 1, ARRAY_CONSTRUCT(2, 1), OBJECT_CONSTRUCT('x', 1) "
            + "UNION ALL SELECT 'z', 'c1', 2, ARRAY_CONSTRUCT(3), OBJECT_CONSTRUCT('x', 2)");
        engine.execute("CREATE TABLE pp (fid VARCHAR, cid VARCHAR, k INT, ids ARRAY, o OBJECT)");
        engine.execute("INSERT INTO pp SELECT 'a', 'c1', 1, ARRAY_CONSTRUCT(1, 2), OBJECT_CONSTRUCT('x', 1) "
            + "UNION ALL SELECT 'y', 'c1', 2, ARRAY_CONSTRUCT(4), OBJECT_CONSTRUCT('x', 9)");
        engine.execute("CREATE TABLE q (fid VARCHAR, cid VARCHAR, k INT, ids ARRAY, o OBJECT)");
    }

    @Test
    public void equalNullBesideKeysOrAloneAnswersAsItsOperatorForm() {
        tables();
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 29",
            "SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE EQUAL_NULL(i.n + o.n, 2))");
        assertAnswer("3",
            "SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE EQUAL_NULL(o.a, i.x))");
        assertAnswer("4",
            "SELECT o.id FROM o WHERE NOT EXISTS (SELECT 1 FROM i WHERE EQUAL_NULL(o.a, i.x)) ORDER "
                + "BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 25",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE EQUAL_NULL(o.a, o.a)) ORDER BY 1");
        assertAnswer("1",
            "SELECT COUNT(*) FROM ee WHERE NOT EXISTS (SELECT 1 FROM pp WHERE ee.cid = pp.cid AND "
                + "ee.k = pp.k AND EQUAL_NULL(ee.fid, pp.fid))");
        assertAnswer("1",
            "SELECT COUNT(*) FROM ee WHERE NOT EXISTS (SELECT 1 FROM pp WHERE ee.cid = pp.cid AND "
                + "ee.k = pp.k AND EQUAL_NULL(ARRAY_SORT(ee.ids), ARRAY_SORT(pp.ids)))");
        assertAnswer("1",
            "SELECT COUNT(*) FROM ee WHERE NOT EXISTS (SELECT 1 FROM pp WHERE ee.cid = pp.cid AND "
                + "ee.k = pp.k AND EQUAL_NULL(OBJECT_PICK(ee.o, 'x'), OBJECT_PICK(pp.o, 'x')))");
        assertAnswer("1",
            "SELECT COUNT(*) FROM ee WHERE NOT EXISTS (SELECT 1 FROM pp WHERE ee.cid = pp.cid AND "
                + "ee.k = pp.k AND ee.fid IS NOT DISTINCT FROM pp.fid)");
        assertAnswer("2",
            "SELECT COUNT(*) FROM ee WHERE NOT EXISTS (SELECT 1 FROM q WHERE ee.cid = q.cid AND ee.k "
                + "= q.k AND EQUAL_NULL(ee.fid, q.fid))");
        assertAnswer("1",
            "SELECT COUNT(*) FROM ee WHERE NOT EXISTS (SELECT 1 FROM pp WHERE EQUAL_NULL(ee.fid, "
                + "pp.fid))");
        assertAnswer("a",
            "SELECT ee.fid FROM ee WHERE EXISTS (SELECT 1 FROM pp WHERE ee.cid = pp.cid AND ee.k = "
                + "pp.k AND EQUAL_NULL(ARRAY_SORT(ee.ids), ARRAY_SORT(pp.ids))) ORDER BY 1");
        assertAnswer("z",
            "SELECT ee.fid FROM ee WHERE NOT EXISTS (SELECT 1 FROM pp WHERE ee.cid = pp.cid AND ee.k "
                + "= pp.k AND EQUAL_NULL(OBJECT_PICK(ee.o, 'x'), OBJECT_PICK(pp.o, 'x')) AND "
                + "EQUAL_NULL(ee.fid, pp.fid)) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 32",
            "SELECT ee.fid FROM ee WHERE NOT EXISTS (SELECT 1 FROM pp WHERE ee.cid = pp.cid AND "
                + "EQUAL_NULL(ee.k + pp.k, 2)) ORDER BY 1");
        assertAnswer("a|true ;; z|false",
            "SELECT ee.fid, EXISTS (SELECT 1 FROM pp WHERE EQUAL_NULL(ee.fid, pp.fid)) FROM ee ORDER "
                + "BY 1");
        assertAnswer("a ;; null",
            "SELECT x.v FROM (SELECT NULL::VARCHAR AS v UNION ALL SELECT 'a' UNION ALL SELECT 'q') x "
                + "WHERE EXISTS (SELECT 1 FROM (SELECT NULL::VARCHAR AS w UNION ALL SELECT 'a') y WHERE "
                + "EQUAL_NULL(x.v, y.w)) ORDER BY 1");
        assertAnswer("q",
            "SELECT x.v FROM (SELECT NULL::VARCHAR AS v UNION ALL SELECT 'a' UNION ALL SELECT 'q') x "
                + "WHERE NOT EXISTS (SELECT 1 FROM i WHERE EQUAL_NULL(x.v, i.x)) ORDER BY 1");
        assertAnswer("1",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE EQUAL_NULL(o.a, 'a')) ORDER BY 1");
        assertAnswer("a ;; null",
            "SELECT x.a FROM (VALUES ('a'), (NULL), ('q')) x(a) WHERE EXISTS (SELECT 1 FROM i WHERE "
                + "EQUAL_NULL(x.a, i.x)) ORDER BY 1");
        assertAnswer("1 ;; 2",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.n = o.n AND EQUAL_NULL(i.x, "
                + "o.a)) ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3 ;; 4",
            "SELECT o.id FROM o WHERE EXISTS (SELECT i.n FROM i GROUP BY i.n HAVING EQUAL_NULL(i.n, "
                + "o.n)) ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3 ;; 4",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE EQUAL_NULL(i.x, o.a) OR "
                + "EQUAL_NULL(i.n, o.n)) ORDER BY 1");
        assertAnswer("",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE EQUAL_NULL(i.n, 99) AND "
                + "EQUAL_NULL(i.x, o.a)) ORDER BY 1");
        assertAnswer("3",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE EQUAL_NULL(o.n, 3)) ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE \"EQUAL_NULL\"(i.x, o.a)) ORDER "
                + "BY 1");
    }

    @Test
    public void arrayContainsOverConstantRowsValuesAndTablesIsAnswered() {
        tables();
        assertAnswer("2",
            "SELECT COUNT(*) FROM (SELECT 'a'::VARCHAR AS fid UNION ALL SELECT 'b'::VARCHAR UNION ALL"
                + " SELECT 'z'::VARCHAR) x WHERE EXISTS (SELECT 1 FROM f WHERE f.ckey = 'c1' AND "
                + "ARRAY_CONTAINS(fid::VARIANT, f.finding_ids))");
        assertAnswer("2",
            "SELECT COUNT(*) FROM e WHERE EXISTS (SELECT 1 FROM f WHERE f.ckey = 'c1' AND "
                + "ARRAY_CONTAINS(e.fid::VARIANT, f.finding_ids))");
        assertAnswer("1",
            "SELECT COUNT(*) FROM e WHERE NOT EXISTS (SELECT 1 FROM f WHERE f.ckey = 'c1' AND"
                + " ARRAY_CONTAINS(e.fid::VARIANT, f.finding_ids))");
        assertAnswer("a ;; b",
            "SELECT e.fid FROM e WHERE EXISTS (SELECT 1 FROM f WHERE ARRAY_CONTAINS(e.fid::VARIANT, "
                + "f.finding_ids)) ORDER BY 1");
        assertAnswer("2",
            "SELECT COUNT(*) FROM (VALUES ('a'), ('b'), ('z')) x(fid) WHERE EXISTS (SELECT 1 FROM f "
                + "WHERE ARRAY_CONTAINS(x.fid::VARIANT, f.finding_ids))");
        assertAnswer("a|true ;; b|true ;; z|false",
            "SELECT e.fid, EXISTS (SELECT 1 FROM f WHERE ARRAY_CONTAINS(e.fid::VARIANT, "
                + "f.finding_ids)) FROM e ORDER BY 1");
        assertAnswer("",
            "SELECT e.fid FROM e WHERE e.fid IN (SELECT f.ckey FROM f WHERE "
                + "ARRAY_CONTAINS(e.fid::VARIANT, f.finding_ids)) ORDER BY 1");
        assertAnswer("a ;; b",
            "SELECT e.fid FROM e WHERE 'c1' IN (SELECT f.ckey FROM f WHERE "
                + "ARRAY_CONTAINS(e.fid::VARIANT, f.finding_ids)) ORDER BY 1");
        assertAnswer("a|1 ;; b|1 ;; z|0",
            "SELECT e.fid, (SELECT COUNT(*) FROM f WHERE ARRAY_CONTAINS(e.fid::VARIANT, "
                + "f.finding_ids)) FROM e ORDER BY 1");
        assertAnswer("1",
            "SELECT COUNT(*) FROM (SELECT 'c1'::VARCHAR AS cid UNION ALL SELECT 'zz'::VARCHAR) x "
                + "WHERE EXISTS (SELECT 1 FROM f WHERE f.ckey = cid)");
        assertAnswer("1",
            "EXECUTE IMMEDIATE $$ DECLARE fid1 VARCHAR DEFAULT 'a'; fid2 VARCHAR DEFAULT 'z'; n INT; "
                + "BEGIN n := (SELECT COUNT(*) FROM (SELECT :fid1::VARCHAR AS fid UNION ALL SELECT "
                + ":fid2::VARCHAR) x WHERE EXISTS (SELECT 1 FROM f WHERE f.ckey = 'c1' AND "
                + "ARRAY_CONTAINS(fid::VARIANT, f.finding_ids))); RETURN n; END; $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 29",
            "SELECT COUNT(*) FROM e WHERE EXISTS (SELECT 1 FROM f WHERE "
                + "ARRAY_CONTAINS(e.fid::VARIANT, ARRAY_APPEND(f.finding_ids, e.fid)))");
        assertAnswer("2",
            "SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE ARRAYS_OVERLAP(i.arr, o.arr))");
        assertAnswer("2",
            "SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE ARRAY_CONTAINS(o.a::VARIANT, "
                + "i.arr))");
        assertAnswer("2",
            "SELECT COUNT(*) FROM o WHERE NOT EXISTS (SELECT 1 FROM i WHERE "
                + "ARRAY_CONTAINS(o.a::VARIANT, i.arr))");
        assertAnswer("1 ;; 2",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE ARRAY_CONTAINS(i.x::VARIANT, "
                + "o.arr)) ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE ARRAY_CONTAINS(o.a::VARIANT, "
                + "ARRAY_CONSTRUCT(i.x, i.s))) ORDER BY 1");
        assertAnswer("1",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE ARRAY_CONTAINS(o.a::VARIANT, "
                + "ARRAY_CONSTRUCT('a'))) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 25",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE ARRAY_CONTAINS((o.a || "
                + "i.x)::VARIANT, i.arr)) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 25",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE ARRAY_CONTAINS(o.a::VARIANT, "
                + "o.arr)) ORDER BY 1");
        assertAnswer("1 ;; 2",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE ARRAY_CONTAINS(o.a::VARIANT, "
                + "i.arr) AND ARRAY_CONTAINS(i.x::VARIANT, o.arr)) ORDER BY 1");
        assertAnswer("",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE ARRAY_CONTAINS(o.a::VARIANT, "
                + "i.arr) AND i.n > o.n) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 25",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE ARRAYS_OVERLAP(o.arr, o.arr)) "
                + "ORDER BY 1");
        assertAnswer("1 ;; 2",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE ARRAY_CONTAINS(o.a::VARIANT, "
                + "i.arr) AND i.s LIKE 'a%') ORDER BY 1");
        assertAnswer("1|true ;; 2|false ;; 3|null",
            "SELECT o.id, ARRAY_CONTAINS(o.a::VARIANT, ARRAY_CONSTRUCT('a')) FROM o WHERE EXISTS "
                + "(SELECT 1 FROM i WHERE i.n = o.n) ORDER BY 1");
        assertAnswer("",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE ARRAY_CONTAINS(i.n::VARIANT, "
                + "o.arr) AND EQUAL_NULL(o.n, i.n)) ORDER BY 1");
    }

    @Test
    public void textPredicateFunctionsKeepingEachArgumentToOneSideAreAnswered() {
        tables();
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 29",
            "SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE CONTAINS(i.s || o.a, 'b'))");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 29",
            "SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE STARTSWITH(i.s, o.a || i.x))");
        assertAnswer("3",
            "SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE REGEXP_LIKE(i.s, o.a || "
                + "'.*'))");
        assertAnswer("3",
            "SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.s RLIKE o.a || '.*')");
        assertAnswer("3",
            "SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE STARTSWITH(i.s, o.a))");
        assertAnswer("3",
            "SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE CONTAINS(i.s, o.a))");
        assertAnswer("2",
            "SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.n = o.n AND CONTAINS(i.s, "
                + "o.a))");
        assertAnswer("1|2 ;; 2|2 ;; 3|0 ;; 4|1",
            "SELECT o.id, (SELECT COUNT(*) FROM i WHERE CONTAINS(i.s, o.a)) FROM o ORDER BY o.id");
        assertAnswer("1",
            "SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE ENDSWITH(i.s, o.a))");
        assertAnswer("3",
            "SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE LIKE(i.s, o.a || '%'))");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 29",
            "SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE IS_NULL_VALUE(o.arr[0]))");
        assertAnswer("0",
            "SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.n = o.n AND "
                + "IS_NULL_VALUE(i.arr[0]))");
        assertAnswer("3",
            "SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE NVL(i.x, 'q') = NVL(o.a, "
                + "'q'))");
        assertAnswer("1 ;; 2 ;; 3 ;; 4",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE CONTAINS(i.s, o.a) OR i.n = o.n) "
                + "ORDER BY 1");
        assertAnswer("1 ;; 2",
            "SELECT o.id FROM o WHERE o.id IN (SELECT i.id FROM i WHERE CONTAINS(i.s, o.a)) ORDER BY "
                + "1");
        assertAnswer("3 ;; 4",
            "SELECT o.id FROM o WHERE o.id NOT IN (SELECT i.id FROM i WHERE CONTAINS(i.s, o.a)) ORDER"
                + " BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 25",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE NOT CONTAINS(i.s, o.a)) ORDER BY "
                + "1");
        assertAnswer("1",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE CONTAINS(o.a, 'a')) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 25",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE CONTAINS(o.a, o.a)) ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE CONTAINS(i.s, i.x) AND i.n = o.n)"
                + " ORDER BY 1");
        assertAnswer("",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE CONTAINS(i.s, UPPER(o.a))) ORDER "
                + "BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 25",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE IFF(o.b, i.n = 1, i.n = 2)) ORDER"
                + " BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 25",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE COALESCE(o.b, FALSE)) ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE NVL(i.n, 0) > o.n) ORDER BY 1");
        assertAnswer("1 ;; 2",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE REGEXP_LIKE(o.a, i.x)) ORDER BY 1");
        assertAnswer("1 ;; 1 ;; 2 ;; 3",
            "SELECT o.id FROM o JOIN i ON EQUAL_NULL(o.a, i.x) ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.x IS NOT DISTINCT FROM o.a) "
                + "ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 25",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE IS_ARRAY(o.arr)) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 25",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE BOOLAND(i.n, o.n)) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 25",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE BOOLOR(i.n, o.n)) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 25",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE BOOLXOR(i.n, o.n)) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 25",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE BOOLNOT(o.n)) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 25",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE IFNULL(o.b, FALSE)) ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 4",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE RLIKE(i.s, o.a || '.*')) ORDER BY"
                + " 1");
        assertAnswer("1 ;; 2 ;; 4",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE ILIKE(i.s, UPPER(o.a) || '%')) "
                + "ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 4",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE LIKE(i.s, o.a || '%', '\\\\')) "
                + "ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 25",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE REGEXP_LIKE(i.s, UPPER(o.a) || "
                + "'.*', 'i')) ORDER BY 1");
        assertAnswer("1 ;; 2",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE CONTAINS('abc', o.a)) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 25",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE ARRAY_CONTAINS(o.a::VARIANT, "
                + "i.arr) = TRUE) ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 4",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i JOIN ee ON CONTAINS(i.s, o.a)) ORDER BY"
                + " 1");
        assertAnswer("1|4 ;; 2|2 ;; 3|null ;; 4|5",
            "SELECT o.id, (SELECT MAX(i.id) FROM i WHERE CONTAINS(i.s, o.a)) FROM o ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 4",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE ARRAY_CONTAINS(o.n::VARIANT, "
                + "i.arr) OR CONTAINS(i.s, o.a)) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 25",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE NVL(o.b, TRUE)) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 25",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE GREATEST(i.n, o.n) = 3) ORDER BY "
                + "1");
        assertAnswer("1 ;; 2",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE COLLATE(i.x, 'en-ci') = o.a) "
                + "ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 25",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE IS_INTEGER(o.n::VARIANT)) ORDER "
                + "BY 1");
        assertAnswer("1 ;; 2 ;; 4",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE (CONTAINS(i.s, o.a))) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 25",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE RLIKE(i.s, UPPER(o.a) || '.*', "
                + "'i')) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 25",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE ILIKE(i.s, o.a || '%', '\\\\')) "
                + "ORDER BY 1");
        assertAnswer("1 ;; 2",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE STARTSWITH(o.a, i.x)) ORDER BY 1");
        assertAnswer("",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE LIKE(i.s, o.a)) ORDER BY 1");
        assertAnswer("1 ;; 2",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE ENDSWITH(o.a, i.x)) ORDER BY 1");
        assertAnswer("1 ;; 2",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE REGEXP_LIKE(o.a, 'a|b')) ORDER BY"
                + " 1");
        assertAnswer("",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE CONTAINS(i.s, o.a) AND "
                + "CONTAINS(i.s, o.a || 'x')) ORDER BY 1");
        assertAnswer("",
            "SELECT o.id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE SYSTEM$TYPEOF(o.a) = 'x') ORDER "
                + "BY 1");
    }
}
