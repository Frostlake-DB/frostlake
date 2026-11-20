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
 * What a materialized view's definition may not hold, refused as the view is created, each in the account's own
 * sentence and place (live-verified): more than one table, joins and self-joins, MINUS and INTERSECT, subqueries,
 * a view read, time travel, window functions, HAVING, QUALIFY, LIMIT, TOP and FETCH, ORDER BY, functions whose value
 * is not deterministic, GROUPING SETS, ROLLUP and CUBE, GROUP BY ALL, DISTINCT aggregates, a GROUP BY key the select
 * list leaves out, and the aggregates a materialized view cannot keep, named as the account plans them.
 */
public class MaterializedViewDefinitionRulesTest extends MeasuredCellsTestSupport {

    @Test
    public void basicRefusals() {
        engine.execute("CREATE TABLE t (a INT, b INT, c STRING)");
        engine.execute("CREATE TABLE u (a INT, d INT)");
        engine.execute("INSERT INTO t VALUES (1, 2, 'x'), (2, 3, 'y')");
        engine.execute("CREATE VIEW v AS SELECT a, b FROM t");
        engine.execute("CREATE FUNCTION f1(x INT) RETURNS INT AS 'x + 1'");
        engine.execute("CREATE MATERIALIZED VIEW m_ok AS SELECT a, b FROM t");
        engine.execute("CREATE MATERIALIZED VIEW m_ok2 AS SELECT a, SUM(b) s, COUNT(*) n FROM t GROUP BY a");
        engine.execute("CREATE MATERIALIZED VIEW m_ok3 AS SELECT a, b FROM t WHERE b > 1");
        assertCells(new String[][] {
            {"CREATE MATERIALIZED VIEW m1 AS SELECT t.a, u.d FROM t JOIN u ON t.a = u.a",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. More than one table referenced in the view definition"},
            {"CREATE MATERIALIZED VIEW m2 AS SELECT x.a, y.b FROM t x JOIN t y ON x.a = y.a",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. Join types [INNER_JOIN] not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m3 AS SELECT t.a, u.d FROM t LEFT JOIN u ON t.a = u.a",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. More than one table referenced in the view definition"},
            {"CREATE MATERIALIZED VIEW m4 AS SELECT t.a, u.d FROM t, u",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. More than one table referenced in the view definition"},
            {"CREATE MATERIALIZED VIEW m5 AS SELECT a, ROW_NUMBER() OVER (ORDER BY a) r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. window functions not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m6 AS SELECT a, SUM(b) OVER (PARTITION BY a) r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. window functions not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m7 AS SELECT a, RANDOM() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 41 | Invalid materialized view "
                + "definition. non deterministic function 'RANDOM' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m8 AS SELECT a, CURRENT_TIMESTAMP() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 41 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m9 AS SELECT a, UUID_STRING() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 41 | Invalid materialized view "
                + "definition. non deterministic function 'UUID_STRING' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m10 AS SELECT a, CURRENT_DATE() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_DATE' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m11 AS SELECT a FROM t WHERE b > RANDOM()",
                "REFUSED SQL compilation error: error line 1 at position 58 | Invalid materialized view "
                + "definition. non deterministic function 'RANDOM' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m12 AS SELECT a, CURRENT_USER() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_USER' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m13 AS SELECT a, SUM(b) s FROM t GROUP BY a HAVING SUM(b) > 1",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. having clause not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m14 AS SELECT a, b FROM t ORDER BY a",
                "REFUSED SQL compilation error: error line 1 at position 51 | Invalid materialized view "
                + "definition: ORDER BY not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m15 AS SELECT a, b FROM t LIMIT 1",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. limit clause not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m16 AS SELECT a, SUM(b) s FROM t GROUP BY GROUPING SETS ((a), ())",
                "REFUSED SQL compilation error: Invalid materialized view: Grouping sets, cube, rollup are "
                + "not supported:  line '1' at position '58'."},
            {"CREATE MATERIALIZED VIEW m17 AS SELECT a, SUM(b) s FROM t GROUP BY ROLLUP (a)",
                "REFUSED SQL compilation error: Invalid materialized view: Grouping sets, cube, rollup are "
                + "not supported:  line '1' at position '58'."},
            {"CREATE MATERIALIZED VIEW m18 AS SELECT a, SUM(b) s FROM t GROUP BY CUBE (a)",
                "REFUSED SQL compilation error: Invalid materialized view: Grouping sets, cube, rollup are "
                + "not supported:  line '1' at position '58'."},
            {"CREATE MATERIALIZED VIEW m19 AS SELECT SUM(b) s FROM t GROUP BY a",
                "REFUSED SQL compilation error: Invalid materialized view definition: GROUP BY key 'T.A' is "
                + "not in the view definition's select list."},
            {"CREATE MATERIALIZED VIEW m22 AS SELECT a FROM t MINUS SELECT a FROM t",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. Join types [MINUS] not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m23 AS SELECT a FROM t INTERSECT SELECT a FROM t",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. Join types [INTERSECT] not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m24 AS SELECT a FROM t WHERE a IN (SELECT a FROM t)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. sub-queries not allowed in view materialized definition."},
            {"CREATE MATERIALIZED VIEW m25 AS SELECT a, (SELECT MAX(b) FROM t) mx FROM t",
                "REFUSED SQL compilation error: error line 1 at position 43 | Invalid materialized view "
                + "definition. sub-queries not allowed in view materialized definition."},
            {"CREATE MATERIALIZED VIEW m28 AS SELECT a FROM t WHERE EXISTS (SELECT 1 FROM u WHERE u.a = "
                + "t.a)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. More than one table referenced in the view definition"},
            {"CREATE MATERIALIZED VIEW m30 AS SELECT a FROM v",
                "REFUSED SQL compilation error: error line 1 at position 46 | Invalid materialized view "
                + "definition. 'V' should be a table."},
            {"CREATE MATERIALIZED VIEW m31 AS SELECT a FROM m_ok",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. More than one table referenced in the view definition"},
            {"CREATE MATERIALIZED VIEW m32 AS SELECT a, COUNT(DISTINCT b) n FROM t GROUP BY a",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. distinct aggregate 'COUNT(DISTINCT T.B)' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m35 AS SELECT a, SUM(DISTINCT b) n FROM t GROUP BY a",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. distinct aggregate 'SUM(DISTINCT T.B)' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m36 AS SELECT a, MEDIAN(b) n FROM t GROUP BY a",
                "REFUSED SQL compilation error: Invalid materialized view: Unsupported aggregate expression "
                + "PERCENTILE_CONT(CAST(T.B AS NUMBER(38,3)), 0.5)."},
            {"CREATE MATERIALIZED VIEW m37 AS SELECT a, LISTAGG(c) n FROM t GROUP BY a",
                "REFUSED SQL compilation error: Invalid materialized view: Unsupported aggregate expression "
                + "LISTAGG(T.C)."},
            {"CREATE MATERIALIZED VIEW m38 AS SELECT a, ARRAY_AGG(c) n FROM t GROUP BY a",
                "REFUSED SQL compilation error: Invalid materialized view: Unsupported aggregate expression "
                + "ARRAY_AGG(T.C)."},
            {"CREATE MATERIALIZED VIEW m43 AS SELECT a, ANY_VALUE(b) n FROM t GROUP BY a",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'ANY_VALUE' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m44 AS SELECT a, MODE(b) n FROM t GROUP BY a",
                "REFUSED SQL compilation error: Invalid materialized view: Unsupported aggregate expression "
                + "MODE(T.B)."},
            {"CREATE MATERIALIZED VIEW m45 AS SELECT a, OBJECT_AGG(c, b::VARIANT) n FROM t GROUP BY a",
                "REFUSED SQL compilation error: Invalid materialized view: Unsupported aggregate expression "
                + "OBJECT_AGG(T.C, CAST(T.B AS VARIANT))."},
            {"CREATE MATERIALIZED VIEW m47 AS SELECT a, HASH_AGG(b) n FROM t GROUP BY a",
                "REFUSED SQL compilation error: Invalid materialized view: Unsupported aggregate expression "
                + "HASH_AGG(T.B)."},
            {"CREATE MATERIALIZED VIEW m48 AS SELECT a, APPROX_PERCENTILE(b, 0.5) n FROM t GROUP BY a",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'APPROX_PERCENTILE_ESTIMATE' not allowed in view "
                + "definition."},
            {"CREATE MATERIALIZED VIEW m51 AS SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[1]')))",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. Source table used in the definition is not accessible or been deleted."},
            {"CREATE MATERIALIZED VIEW m53 AS SELECT a, b FROM t QUALIFY a > 0",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. qualify clause not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m55 AS SELECT a, b FROM t AT(OFFSET => -1)",
                "REFUSED SQL compilation error: Invalid materialized view: Time travel on base table in line "
                + "1 at position 49."},
            {"CREATE MATERIALIZED VIEW m57 AS SELECT a, CURRENT_DATABASE() d FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_DATABASE' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m58 AS SELECT a, SEQ4() s FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'SEQ4' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m62 COPY GRANTS AS SELECT a FROM t",
                "REFUSED SQL compilation error: | Invalid operation COPY GRANTS without specifying source "
                + "object."},
            {"CREATE MATERIALIZED VIEW m63 AS SELECT a, b FROM t ORDER BY a LIMIT 1",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. limit clause not allowed in view definition."},
        });
    }

    @Test
    public void moreRefusals() {
        engine.execute("CREATE TABLE t (a INT, b INT, c STRING, f FLOAT, n NUMBER(10,2), v VARIANT)");
        engine.execute("CREATE TABLE u (a INT, d INT)");
        engine.execute("CREATE VIEW vv AS SELECT a, b FROM t");
        assertCells(new String[][] {
            {"CREATE MATERIALIZED VIEW m1 AS SELECT x.a, COUNT(DISTINCT x.b) n FROM t x GROUP BY x.a",
                "REFUSED SQL compilation error: error line 1 at position 43 | Invalid materialized view "
                + "definition. distinct aggregate 'COUNT(DISTINCT X.B)' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m3 AS SELECT a, MEDIAN(f) n FROM t GROUP BY a",
                "REFUSED SQL compilation error: Invalid materialized view: Unsupported aggregate expression "
                + "PERCENTILE_CONT(T.F, 0.5)."},
            {"CREATE MATERIALIZED VIEW m4 AS SELECT a, MEDIAN(n) n FROM t GROUP BY a",
                "REFUSED SQL compilation error: Invalid materialized view: Unsupported aggregate expression "
                + "PERCENTILE_CONT(CAST(T.N AS NUMBER(13,5)), 0.5)."},
            {"CREATE MATERIALIZED VIEW m5 AS SELECT a, LISTAGG(c, ',') n FROM t GROUP BY a",
                "REFUSED SQL compilation error: Invalid materialized view: Unsupported aggregate expression "
                + "LISTAGG(T.C, ',')."},
            {"CREATE MATERIALIZED VIEW m10 AS SELECT a, MAX_BY(c, b) n FROM t GROUP BY a",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'MAX_BY' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m12 AS SELECT a, APPROX_TOP_K(b) n FROM t GROUP BY a",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'APPROX_TOP_K' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m15 AS SELECT a, PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY b) n "
                + "FROM t GROUP BY a",
                "REFUSED SQL compilation error: Invalid materialized view: Unsupported aggregate expression "
                + "PERCENTILE_CONT(CAST(T.B AS NUMBER(38,3)), 0.5)."},
            {"CREATE MATERIALIZED VIEW m16 AS SELECT a, PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY b) n "
                + "FROM t GROUP BY a",
                "REFUSED SQL compilation error: Invalid materialized view: Unsupported aggregate expression "
                + "PERCENTILE_DISC(T.B, 0.5)."},
            {"CREATE MATERIALIZED VIEW m18 AS SELECT a, MIN_BY(c, b) n FROM t GROUP BY a",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'MIN_BY' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m19 AS SELECT a, ARRAY_AGG(DISTINCT c) n FROM t GROUP BY a",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. distinct aggregate 'ARRAY_AGG(DISTINCT T.C)' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m25 AS SELECT a, LISTAGG(DISTINCT c) n FROM t GROUP BY a",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. distinct aggregate 'LISTAGG(DISTINCT T.C)' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m26 AS SELECT a, MEDIAN(b + 1) n FROM t GROUP BY a",
                "REFUSED SQL compilation error: Invalid materialized view: Unsupported aggregate expression "
                + "PERCENTILE_CONT(CAST(T.B + 1 AS NUMBER(38,3)), 0.5)."},
            {"CREATE MATERIALIZED VIEW m27 AS SELECT a, ARRAY_AGG(c) WITHIN GROUP (ORDER BY c) n FROM t "
                + "GROUP BY a",
                "REFUSED SQL compilation error: Invalid materialized view: Unsupported aggregate expression "
                + "ARRAY_AGG(T.C)."},
            {"CREATE MATERIALIZED VIEW m28 AS SELECT a, OBJECT_AGG(c, v) n FROM t GROUP BY a",
                "REFUSED SQL compilation error: Invalid materialized view: Unsupported aggregate expression "
                + "OBJECT_AGG(T.C, T.V)."},
            {"CREATE MATERIALIZED VIEW p1 AS SELECT a, SUM(DISTINCT b) n FROM t x GROUP BY a",
                "REFUSED SQL compilation error: error line 1 at position 41 | Invalid materialized view "
                + "definition. distinct aggregate 'SUM(DISTINCT X.B)' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW c1 AS SELECT a FROM t WHERE b > (SELECT MIN(b) FROM t)",
                "REFUSED SQL compilation error: error line 1 at position 58 | Invalid materialized view "
                + "definition. sub-queries not allowed in view materialized definition."},
            {"CREATE MATERIALIZED VIEW c2 AS SELECT a FROM t WHERE a IN (SELECT a FROM u)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. More than one table referenced in the view definition"},
            {"CREATE MATERIALIZED VIEW c3 AS SELECT a FROM t WHERE EXISTS (SELECT 1 FROM t t2 WHERE t2.a ="
                + " t.a)",
                "REFUSED SQL compilation error: error line 1 at position 53 | Invalid materialized view "
                + "definition. sub-queries not allowed in view materialized definition."},
            {"CREATE MATERIALIZED VIEW c4 AS SELECT a FROM t UNION ALL SELECT a FROM u",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. More than one table referenced in the view definition"},
            {"CREATE MATERIALIZED VIEW c5 AS WITH c AS (SELECT a FROM t) SELECT c.a FROM c JOIN u ON c.a ="
                + " u.a",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. More than one table referenced in the view definition"},
            {"CREATE MATERIALIZED VIEW c6 AS SELECT x.a FROM t x JOIN (SELECT a FROM t) y ON x.a = y.a",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. Join types [INNER_JOIN] not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW c7 AS SELECT x.a FROM t x, t y",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. Join types [INNER_JOIN] not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW c8 AS SELECT x.a FROM t x LEFT JOIN t y ON x.a = y.a",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. Join types [LEFT_OUTER_JOIN] not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW c9 AS SELECT x.a FROM t x NATURAL JOIN t y",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. Join types [INNER_JOIN] not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW c10 AS SELECT x.a FROM t x FULL JOIN t y ON x.a = y.a",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. Join types [FULL_OUTER_JOIN] not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW c11 AS SELECT a FROM t WHERE a NOT IN (SELECT a FROM t)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. sub-queries not allowed in view materialized definition."},
            {"CREATE MATERIALIZED VIEW c12 AS SELECT a FROM t EXCEPT SELECT a FROM t",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. Join types [MINUS] not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW c13 AS SELECT a, (SELECT 1) one FROM t",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. More than one table referenced in the view definition"},
            {"CREATE MATERIALIZED VIEW c14 AS SELECT a FROM t UNION ALL SELECT a FROM t ORDER BY a",
                "REFUSED SQL compilation error: error line 1 at position 74 | Invalid materialized view "
                + "definition: ORDER BY not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW c15 AS SELECT a FROM t x WHERE EXISTS (SELECT 1 FROM t y WHERE y.a "
                + "= x.a)",
                "REFUSED SQL compilation error: error line 1 at position 56 | Invalid materialized view "
                + "definition. sub-queries not allowed in view materialized definition."},
            {"CREATE MATERIALIZED VIEW c16 AS SELECT x.a FROM t x RIGHT JOIN t y ON x.a = y.a",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. Join types [RIGHT_OUTER_JOIN] not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW c17 AS SELECT x.a FROM t x CROSS JOIN t y",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. Join types [INNER_JOIN] not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r1 AS SELECT a, random() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 41 | Invalid materialized view "
                + "definition. non deterministic function 'RANDOM' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r4 AS SELECT a, CURRENT_TIME() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 41 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIME' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r5 AS SELECT a, LOCALTIMESTAMP() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 41 | Invalid materialized view "
                + "definition. non deterministic function 'LOCALTIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r6 AS SELECT a, CURRENT_ROLE() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 41 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_ROLE' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r7 AS SELECT a, CURRENT_SCHEMA() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 41 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_SCHEMA' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r8 AS SELECT a, CURRENT_WAREHOUSE() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 41 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_WAREHOUSE' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r9 AS SELECT a, CURRENT_ACCOUNT() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 41 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_ACCOUNT' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r10 AS SELECT a, UNIFORM(1, 10, RANDOM()) r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'UNIFORM' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r11 AS SELECT a, RANDSTR(5, 1) r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'RANDSTR' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r12 AS SELECT a, SEQ8() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'SEQ8' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r13 AS SELECT a, CURRENT_SESSION() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_SESSION' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r14 AS SELECT a, LAST_QUERY_ID() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'LAST_QUERY_ID' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r15 AS SELECT a, CURRENT_TIMESTAMP r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r16 AS SELECT a, CURRENT_DATE r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_DATE' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r18 AS SELECT a, NORMAL(0, 1, 1) r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'NORMAL' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r19 AS SELECT a, CURRENT_REGION() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_REGION' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r20 AS SELECT a, CURRENT_VERSION() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_VERSION' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r21 AS SELECT a, DATEADD(day, 1, CURRENT_DATE()) r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 58 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_DATE' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r22 AS SELECT a, UNIFORM(1, 10, 5) r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'UNIFORM' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r23 AS SELECT a, CURRENT_CLIENT() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_CLIENT' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r24 AS SELECT a, CURRENT_USER r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_USER' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r25 AS SELECT a, RANDOM(42) r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'RANDOM' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r26 AS SELECT a, CURRENT_IP_ADDRESS() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_IP_ADDRESS' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r27 AS SELECT a, CURRENT_STATEMENT() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_STATEMENT' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r28 AS SELECT a, CURRENT_TRANSACTION() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TRANSACTION' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r30 AS SELECT a, LOCALTIME() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'LOCALTIME' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r31 AS SELECT a, IS_ROLE_IN_SESSION('PUBLIC') r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'IS_ROLE_IN_SESSION' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r32 AS SELECT a, CURRENT_AVAILABLE_ROLES() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_AVAILABLE_ROLES' not allowed in view "
                + "definition."},
            {"CREATE MATERIALIZED VIEW r33 AS SELECT a, CURRENT_SECONDARY_ROLES() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_SECONDARY_ROLES' not allowed in view "
                + "definition."},
            {"CREATE MATERIALIZED VIEW r34 AS SELECT a, CURRENT_ORGANIZATION_NAME() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_ORGANIZATION_NAME' not allowed in view "
                + "definition."},
            {"CREATE MATERIALIZED VIEW r35 AS SELECT a, CURRENT_ACCOUNT_NAME() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_ACCOUNT_NAME' not allowed in view "
                + "definition."},
            {"CREATE MATERIALIZED VIEW r36 AS SELECT a, ZIPF(1, 10, RANDOM()) r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'ZIPF' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r37 AS SELECT a, UUID_STRING() r, RANDOM() q FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'UUID_STRING' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW ml1\nAS SELECT a,\n  RANDOM() r FROM t",
                "REFUSED SQL compilation error: error line 3 at position 2 | Invalid materialized view "
                + "definition. non deterministic function 'RANDOM' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW\n  ml2 AS SELECT a, ROW_NUMBER() OVER (ORDER BY a) r FROM t",
                "REFUSED SQL compilation error: error line 2 at position 2 | Invalid materialized view "
                + "definition. window functions not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW ml3 AS SELECT a, b FROM t\nORDER BY a",
                "REFUSED SQL compilation error: error line 2 at position 0 | Invalid materialized view "
                + "definition: ORDER BY not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW ml4 AS SELECT a, SUM(b) s FROM t\n  GROUP BY ROLLUP (a)",
                "REFUSED SQL compilation error: Invalid materialized view: Grouping sets, cube, rollup are "
                + "not supported:  line '2' at position '2'."},
            {"CREATE MATERIALIZED VIEW ml5 AS SELECT a FROM\n  vv",
                "REFUSED SQL compilation error: error line 2 at position 2 | Invalid materialized view "
                + "definition. 'VV' should be a table."},
            {"CREATE MATERIALIZED VIEW ml6 AS SELECT a FROM\n   t AT(OFFSET => -1)",
                "REFUSED SQL compilation error: Invalid materialized view: Time travel on base table in line "
                + "2 at position 3."},
            {"CREATE MATERIALIZED VIEW ml8 AS SELECT a,\n (SELECT MAX(b) FROM t) mx FROM t",
                "REFUSED SQL compilation error: error line 2 at position 2 | Invalid materialized view "
                + "definition. sub-queries not allowed in view materialized definition."},
            {"CREATE MATERIALIZED VIEW ml9 AS SELECT a, COUNT(\nDISTINCT b) n FROM t GROUP BY a",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. distinct aggregate 'COUNT(DISTINCT T.B)' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW o1 AS SELECT a, SUM(b) s, ROW_NUMBER() OVER (ORDER BY a) r FROM t "
                + "GROUP BY a HAVING SUM(b) > 0 LIMIT 1",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. window functions not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW o2 AS SELECT a, SUM(b) s FROM t GROUP BY a HAVING SUM(b) > 0 LIMIT "
                + "1",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. having clause not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW o3 AS SELECT a, RANDOM() r, ROW_NUMBER() OVER (ORDER BY a) w FROM t",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. window functions not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW o4 AS SELECT t.a, ROW_NUMBER() OVER (ORDER BY t.a) w FROM t JOIN u "
                + "ON t.a = u.a",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. More than one table referenced in the view definition"},
            {"CREATE MATERIALIZED VIEW o5 AS SELECT a, b FROM t ORDER BY RANDOM()",
                "REFUSED SQL compilation error: error line 1 at position 50 | Invalid materialized view "
                + "definition: ORDER BY not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW o6 AS SELECT a, COUNT(DISTINCT b) n, LISTAGG(c) l FROM t GROUP BY a",
                "REFUSED SQL compilation error: error line 1 at position 41 | Invalid materialized view "
                + "definition. distinct aggregate 'COUNT(DISTINCT T.B)' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW o7 AS SELECT a, LISTAGG(c) l, COUNT(DISTINCT b) n FROM t GROUP BY a",
                "REFUSED SQL compilation error: error line 1 at position 55 | Invalid materialized view "
                + "definition. distinct aggregate 'COUNT(DISTINCT T.B)' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW o8 AS SELECT a, RANDOM() r FROM t GROUP BY a HAVING COUNT(*) > 1",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. having clause not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW o9 AS SELECT a, b FROM t QUALIFY ROW_NUMBER() OVER (ORDER BY a) = 1",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. qualify clause not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW o10 AS SELECT a, LISTAGG(c) l FROM t GROUP BY ROLLUP (a)",
                "REFUSED SQL compilation error: Invalid materialized view: Grouping sets, cube, rollup are "
                + "not supported:  line '1' at position '62'."},
            {"CREATE MATERIALIZED VIEW o11 AS SELECT SUM(b) s FROM t GROUP BY a HAVING SUM(b) > 0",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. having clause not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW o12 AS SELECT a, RANDOM() r FROM vv",
                "REFUSED SQL compilation error: error line 1 at position 58 | Invalid materialized view "
                + "definition. 'VV' should be a table."},
            {"CREATE MATERIALIZED VIEW o13 AS SELECT a, SUM(b) s FROM t GROUP BY a ORDER BY a",
                "REFUSED SQL compilation error: error line 1 at position 69 | Invalid materialized view "
                + "definition: ORDER BY not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW o14 AS SELECT a, (SELECT MAX(b) FROM t) mx, RANDOM() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 43 | Invalid materialized view "
                + "definition. sub-queries not allowed in view materialized definition."},
            {"CREATE MATERIALIZED VIEW o15 AS SELECT a, RANDOM() r, (SELECT MAX(b) FROM t) mx FROM t",
                "REFUSED SQL compilation error: error line 1 at position 55 | Invalid materialized view "
                + "definition. sub-queries not allowed in view materialized definition."},
            {"CREATE MATERIALIZED VIEW o16 AS SELECT SUM(b) s, LISTAGG(c) l FROM t GROUP BY a",
                "REFUSED SQL compilation error: Invalid materialized view definition: GROUP BY key 'T.A' is "
                + "not in the view definition's select list."},
            {"CREATE MATERIALIZED VIEW k1 AS SELECT a, SUM(b) s FROM t GROUP BY a, c",
                "REFUSED SQL compilation error: Invalid materialized view definition: GROUP BY key 'T.C' is "
                + "not in the view definition's select list."},
            {"CREATE MATERIALIZED VIEW k3 AS SELECT SUM(b) s FROM t GROUP BY a + 1",
                "REFUSED SQL compilation error: Invalid materialized view definition: GROUP BY key 'T.A + 1' "
                + "is not in the view definition's select list."},
            {"CREATE MATERIALIZED VIEW k4 AS SELECT x.a, SUM(x.b) s FROM t x GROUP BY x.a, x.c",
                "REFUSED SQL compilation error: Invalid materialized view definition: GROUP BY key 'X.C' is "
                + "not in the view definition's select list."},
            {"CREATE MATERIALIZED VIEW k6 AS SELECT c, SUM(b) s FROM t GROUP BY a, c",
                "REFUSED SQL compilation error: Invalid materialized view definition: GROUP BY key 'T.A' is "
                + "not in the view definition's select list."},
            {"CREATE MATERIALIZED VIEW k7 AS SELECT a FROM t GROUP BY a, b",
                "REFUSED SQL compilation error: Invalid materialized view definition: GROUP BY key 'T.B' is "
                + "not in the view definition's select list."},
            {"CREATE MATERIALIZED VIEW k8 AS SELECT a, SUM(b) s FROM t GROUP BY ALL",
                "REFUSED SQL compilation error: Invalid materialized view definition. GROUP BY ALL not "
                + "allowed in MV definition."},
            {"CREATE MATERIALIZED VIEW e1 AS SELECT TOP 1 a FROM t",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. limit clause not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW e2 AS SELECT a FROM t FETCH FIRST 1 ROWS ONLY",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. limit clause not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW e3 AS SELECT a FROM t LIMIT 1 OFFSET 1",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. limit clause not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW e4 AS SELECT a FROM t OFFSET 1 ROWS",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected '<EOF>'."},
            {"CREATE MATERIALIZED VIEW e8 AS SELECT a FROM (SELECT a FROM t LIMIT 1)",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. limit clause not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW e9 AS SELECT a FROM (SELECT a, ROW_NUMBER() OVER (ORDER BY a) r "
                + "FROM t)",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. window functions not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW e10 AS SELECT a FROM (SELECT a, RANDOM() r FROM t)",
                "REFUSED SQL compilation error: error line 1 at position 57 | Invalid materialized view "
                + "definition. non deterministic function 'RANDOM' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW e11 AS WITH c AS (SELECT a, b FROM t ORDER BY a) SELECT a FROM c",
                "REFUSED SQL compilation error: error line 1 at position 62 | Invalid materialized view "
                + "definition: ORDER BY not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW e12 AS SELECT a FROM t UNION ALL SELECT a FROM t LIMIT 1",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. limit clause not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW e13 AS (SELECT a FROM t LIMIT 1)",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. limit clause not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW e15 AS SELECT a, b FROM t BEFORE(OFFSET => -1)",
                "REFUSED SQL compilation error: Invalid materialized view: Time travel on base table in line "
                + "1 at position 49."},
            {"CREATE MATERIALIZED VIEW e16 AS SELECT a, b FROM TABLE(t)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ')'. | syntax"
                + " error line 1 at position 57 unexpected '<EOF>'."},
            {"CREATE MATERIALIZED VIEW e17 AS SELECT a FROM t GROUP BY CUBE (a)",
                "REFUSED SQL compilation error: Invalid materialized view: Grouping sets, cube, rollup are "
                + "not supported:  line '1' at position '48'."},
            {"CREATE MATERIALIZED VIEW e20 AS SELECT a, SUM(b) s FROM t GROUP BY a HAVING a > 0",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. having clause not allowed in view definition."},
        });
    }

    @Test
    public void refusalOrder() {
        engine.execute("CREATE TABLE t (a INT, b INT, c STRING)");
        assertCells(new String[][] {
            {"CREATE MATERIALIZED VIEW q1 AS SELECT a, ROW_NUMBER() OVER (ORDER BY a) r FROM t QUALIFY r ="
                + " 1",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. window functions not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW q2 AS SELECT a, SUM(b) s FROM t GROUP BY a HAVING SUM(b) > 0 "
                + "QUALIFY ROW_NUMBER() OVER (ORDER BY a) = 1",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. having clause not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW q3 AS SELECT a, b FROM t QUALIFY a > 0 LIMIT 1",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. qualify clause not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW q4 AS SELECT a, (SELECT MAX(b) FROM t) m, ROW_NUMBER() OVER (ORDER "
                + "BY a) r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. window functions not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW q5 AS SELECT a, b FROM t ORDER BY a LIMIT 1",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. limit clause not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW q6 AS SELECT a, ROW_NUMBER() OVER (ORDER BY a) r FROM t ORDER BY a",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. window functions not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW q7 AS SELECT a, (SELECT MAX(b) FROM t) m FROM t ORDER BY a",
                "REFUSED SQL compilation error: error line 1 at position 73 | Invalid materialized view "
                + "definition: ORDER BY not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW q10 AS SELECT a, b, COUNT(*) n FROM t GROUP BY GROUPING SETS (a, b)",
                "REFUSED SQL compilation error: Invalid materialized view: Grouping sets, cube, rollup are "
                + "not supported:  line '1' at position '63'."},
            {"CREATE MATERIALIZED VIEW q11 AS SELECT a, COUNT(*) n FROM t GROUP BY ROLLUP (a), b",
                "REFUSED SQL compilation error: Invalid materialized view: Grouping sets, cube, rollup are "
                + "not supported:  line '1' at position '60'."},
            {"CREATE MATERIALIZED VIEW q12 AS SELECT a, COUNT(*) n FROM t GROUP BY GROUPING SETS (b)",
                "REFUSED SQL compilation error: Invalid materialized view definition: GROUP BY key 'T.B' is "
                + "not in the view definition's select list."},
            {"CREATE MATERIALIZED VIEW q14 AS SELECT a FROM t WHERE NOT EXISTS (SELECT 1 FROM t t2 WHERE "
                + "t2.a = t.a)",
                "REFUSED SQL compilation error: error line 1 at position 58 | Invalid materialized view "
                + "definition. sub-queries not allowed in view materialized definition."},
            {"CREATE MATERIALIZED VIEW q15 AS SELECT a FROM t WHERE b = ANY (SELECT b FROM t)",
                "REFUSED SQL compilation error: error line 1 at position 58 | Invalid materialized view "
                + "definition. sub-queries not allowed in view materialized definition."},
            {"CREATE MATERIALIZED VIEW q16 AS SELECT a, ROW_NUMBER() OVER (ORDER BY a) r, RANDOM() q FROM "
                + "t QUALIFY r = 1",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. window functions not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW q17 AS SELECT a FROM t WHERE a IN (SELECT a FROM t) ORDER BY a",
                "REFUSED SQL compilation error: error line 1 at position 77 | Invalid materialized view "
                + "definition: ORDER BY not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW q18 AS SELECT DISTINCT a FROM t ORDER BY a",
                "REFUSED SQL compilation error: error line 1 at position 57 | Invalid materialized view "
                + "definition: ORDER BY not allowed in view definition."},
        });
    }
}
