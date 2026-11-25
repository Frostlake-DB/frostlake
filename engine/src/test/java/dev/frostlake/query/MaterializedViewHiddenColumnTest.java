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
 * What a materialized view's definition is refused for where it selects the hidden partition column an UNPIVOT keeps,
 * where SYSDATE, GETDATE and SYSTIMESTAMP are reported, and where a column the select list leaves unnamed is reported
 * among the definition's other refusals (live-verified).
 */
public class MaterializedViewHiddenColumnTest extends MeasuredCellsTestSupport {

    @Test
    public void definitionRefusals() {
        engine.execute("CREATE TABLE t (a INT, b INT, c STRING, f FLOAT)");
        engine.execute("CREATE TABLE t3 (a INT, b NUMBER(10,2), c STRING)");
        engine.execute("CREATE TABLE u (a INT)");
        engine.execute("CREATE VIEW vv AS SELECT a FROM t");
        assertCells(new String[][] {
            {"CREATE MATERIALIZED VIEW u1 AS SELECT * FROM (SELECT a, b, c FROM t) UNPIVOT(val FOR col IN "
                + "(a, b))",
                "REFUSED SQL compilation error: | 'SYS_MV_SOURCE_PARTITION' cannot be used as a column name "
                + "for materialized views."},
            {"CREATE MATERIALIZED VIEW u2 AS SELECT * FROM t UNPIVOT(val FOR col IN (a, b))",
                "REFUSED SQL compilation error: | 'SYS_MV_SOURCE_PARTITION' cannot be used as a column name "
                + "for materialized views."},
            {"CREATE MATERIALIZED VIEW u5 AS SELECT * FROM t UNPIVOT INCLUDE NULLS (val FOR col IN (a, b))",
                "REFUSED SQL compilation error: | 'SYS_MV_SOURCE_PARTITION' cannot be used as a column name "
                + "for materialized views."},
            {"CREATE MATERIALIZED VIEW u9 AS SELECT * FROM t UNPIVOT(val FOR col IN (a, b)) WHERE RANDOM()"
                + " > 0",
                "REFUSED SQL compilation error: | 'SYS_MV_SOURCE_PARTITION' cannot be used as a column name "
                + "for materialized views."},
            {"CREATE MATERIALIZED VIEW u10 AS SELECT * FROM t UNPIVOT(val FOR col IN (a, b)) LIMIT 1",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. limit clause not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW u11 AS SELECT * FROM t UNPIVOT(val FOR col IN (a, b)) UNION ALL "
                + "SELECT * FROM t UNPIVOT(val FOR col IN (a, b))",
                "REFUSED SQL compilation error: | duplicate column name 'SYS_MV_SOURCE_PARTITION'"},
            {"CREATE MATERIALIZED VIEW u13 AS SELECT * FROM t UNPIVOT(val FOR col IN (a, b)) ORDER BY c",
                "REFUSED SQL compilation error: error line 1 at position 79 | Invalid materialized view "
                + "definition: ORDER BY not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW u14 AS SELECT * FROM (SELECT * FROM t UNPIVOT(val FOR col IN (a, "
                + "b)))",
                "REFUSED SQL compilation error: | duplicate column name 'SYS_MV_SOURCE_PARTITION'"},
            {"CREATE OR REPLACE MATERIALIZED VIEW u15 AS SELECT * FROM t UNPIVOT(val FOR col IN (a, b))",
                "REFUSED SQL compilation error: | 'SYS_MV_SOURCE_PARTITION' cannot be used as a column name "
                + "for materialized views."},
            {"CREATE MATERIALIZED VIEW IF NOT EXISTS u16 AS SELECT * FROM t UNPIVOT(val FOR col IN (a, b))",
                "REFUSED SQL compilation error: | 'SYS_MV_SOURCE_PARTITION' cannot be used as a column name "
                + "for materialized views."},
            {"CREATE MATERIALIZED VIEW v4 AS SELECT x.* FROM t x UNPIVOT(val FOR col IN (a, b))",
                "REFUSED SQL compilation error: | 'SYS_MV_SOURCE_PARTITION' cannot be used as a column name "
                + "for materialized views."},
            {"CREATE MATERIALIZED VIEW v5 AS SELECT *, c AS c2 FROM t UNPIVOT(val FOR col IN (a, b))",
                "REFUSED SQL compilation error: | duplicate column name 'SYS_MV_SOURCE_PARTITION'"},
            {"CREATE MATERIALIZED VIEW v6 AS SELECT * EXCLUDE c FROM t UNPIVOT(val FOR col IN (a, b))",
                "REFUSED SQL compilation error: | 'SYS_MV_SOURCE_PARTITION' cannot be used as a column name "
                + "for materialized views."},
            {"CREATE MATERIALIZED VIEW v7 AS WITH q AS (SELECT * FROM t UNPIVOT(val FOR col IN (a, b))) "
                + "SELECT * FROM q",
                "REFUSED SQL compilation error: | duplicate column name 'SYS_MV_SOURCE_PARTITION'"},
            {"CREATE MATERIALIZED VIEW v10 AS SELECT * FROM (SELECT * FROM t) UNPIVOT(val FOR col IN (a, "
                + "b))",
                "REFUSED SQL compilation error: | 'SYS_MV_SOURCE_PARTITION' cannot be used as a column name "
                + "for materialized views."},
            {"CREATE MATERIALIZED VIEW v12 AS SELECT * FROM t UNPIVOT(val FOR col IN (a, b)) WHERE c IN "
                + "(SELECT c FROM t)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. sub-queries not allowed in view materialized definition."},
            {"CREATE MATERIALIZED VIEW v13 AS SELECT * FROM t UNPIVOT(val FOR col IN (a, b)) QUALIFY "
                + "ROW_NUMBER() OVER (ORDER BY c) = 1",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. qualify clause not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW v14 AS SELECT *, ROW_NUMBER() OVER (ORDER BY c) r FROM t "
                + "UNPIVOT(val FOR col IN (a, b))",
                "REFUSED SQL compilation error: | duplicate column name 'SYS_MV_SOURCE_PARTITION'"},
            {"CREATE MATERIALIZED VIEW v15 AS SELECT * FROM t UNPIVOT(val FOR col IN (a, b)), t u",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. Join types [INNER_JOIN] not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW v16 AS SELECT * FROM t AT(OFFSET => -1) UNPIVOT(val FOR col IN (a, "
                + "b))",
                "REFUSED SQL compilation error: Invalid materialized view: Time travel on base table in line "
                + "1 at position 46."},
            {"CREATE MATERIALIZED VIEW v17 AS SELECT * FROM t UNPIVOT(val FOR col IN (a, b)) GROUP BY "
                + "GROUPING SETS ((c), (col))",
                "REFUSED SQL compilation error: Invalid materialized view: Grouping sets, cube, rollup are "
                + "not supported:  line '1' at position '79'."},
            {"CREATE MATERIALIZED VIEW v18 AS SELECT * FROM t UNPIVOT(val FOR col IN (a, b)) WHERE "
                + "UUID_STRING() > 'a'",
                "REFUSED SQL compilation error: | 'SYS_MV_SOURCE_PARTITION' cannot be used as a column name "
                + "for materialized views."},
            {"CREATE MATERIALIZED VIEW v19 AS SELECT * FROM t UNPIVOT(val FOR col IN (a, b)) GROUP BY ALL",
                "REFUSED SQL compilation error: | 'SYS_MV_SOURCE_PARTITION' cannot be used as a column name "
                + "for materialized views."},
            {"CREATE MATERIALIZED VIEW v20 AS SELECT DISTINCT * FROM t UNPIVOT(val FOR col IN (a, b))",
                "REFUSED SQL compilation error: | 'SYS_MV_SOURCE_PARTITION' cannot be used as a column name "
                + "for materialized views."},
            {"CREATE MATERIALIZED VIEW v21 AS SELECT * FROM t UNPIVOT(val FOR col IN (a, b)) HAVING "
                + "COUNT(*) > 0",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid materialized view "
                + "definition. having clause not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW v22 AS SELECT * FROM (SELECT * FROM t UNPIVOT(val FOR col IN (a, "
                + "b))) WHERE RANDOM() > 0",
                "REFUSED SQL compilation error: | duplicate column name 'SYS_MV_SOURCE_PARTITION'"},
            {"CREATE MATERIALIZED VIEW v23 AS SELECT * FROM t UNPIVOT(val FOR col IN (a, b)) UNION ALL "
                + "SELECT * FROM t UNPIVOT(val FOR col IN (a, b)) LIMIT 1",
                "REFUSED SQL compilation error: | duplicate column name 'SYS_MV_SOURCE_PARTITION'"},
            {"CREATE MATERIALIZED VIEW r1 AS SELECT a, SYSDATE() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 35 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r2 AS SELECT SYSDATE() r, a FROM t",
                "REFUSED SQL compilation error: error line 1 at position 35 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r3x AS SELECT a, SYSDATE() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 35 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r4 AS SELECT a, b, c, SYSDATE() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 35 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r5 AS\nSELECT a,\n  SYSDATE() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 35 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r6 AS SELECT a FROM t WHERE SYSDATE() > '2020-01-01'",
                "REFUSED SQL compilation error: error line 1 at position 35 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r7 AS SELECT a, sysdate() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 35 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r8 AS SELECT a, GETDATE() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 1 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r9 AS SELECT GETDATE() r, a FROM t",
                "REFUSED SQL compilation error: error line 1 at position 1 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r10 AS\nSELECT a,\n  GETDATE() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 1 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r11 AS SELECT a, SYSTIMESTAMP() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 1 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r12 AS\nSELECT a,\n  SYSTIMESTAMP() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 1 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r13 AS SELECT a, CURRENT_TIMESTAMP() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r14 AS SELECT a, SYSDATE() r, RANDOM() q FROM t",
                "REFUSED SQL compilation error: error line 1 at position 35 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r15 AS SELECT a, RANDOM() q, SYSDATE() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'RANDOM' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r16 AS SELECT a, GETDATE() r, RANDOM() q FROM t",
                "REFUSED SQL compilation error: error line 1 at position 1 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r17 AS SELECT a, RANDOM() q, GETDATE() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 42 | Invalid materialized view "
                + "definition. non deterministic function 'RANDOM' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r18 AS   SELECT a, SYSDATE() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 35 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r19 AS SELECT a, DATEADD(day, 1, SYSDATE()) r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 35 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r20 AS SELECT a, DATEADD(day, 1, GETDATE()) r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 1 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r22 AS SELECT a, b, SYSDATE() r FROM t",
                "REFUSED SQL compilation error: error line 1 at position 35 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r23 AS SELECT a, SYSDATE(), b FROM t",
                "REFUSED SQL compilation error: | Missing column specification"},
            {"CREATE MATERIALIZED VIEW r24 AS SELECT SYSDATE() FROM t",
                "REFUSED SQL compilation error: | Missing column specification"},
            {"CREATE MATERIALIZED VIEW r25 AS SELECT a FROM t WHERE GETDATE() > '2020-01-01'",
                "REFUSED SQL compilation error: error line 1 at position 1 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW r26 AS SELECT a, SYSDATE() r, SYSDATE() s FROM t",
                "REFUSED SQL compilation error: error line 1 at position 35 | Invalid materialized view "
                + "definition. non deterministic function 'CURRENT_TIMESTAMP' not allowed in view definition."},
            {"CREATE MATERIALIZED VIEW m1 AS SELECT a, b + 1 FROM t LIMIT 1",
                "REFUSED SQL compilation error: | Missing column specification"},
            {"CREATE MATERIALIZED VIEW m2 AS SELECT a, b + 1 FROM t ORDER BY a",
                "REFUSED SQL compilation error: | Missing column specification"},
            {"CREATE MATERIALIZED VIEW m3 AS SELECT a, ROW_NUMBER() OVER (ORDER BY a) FROM t",
                "REFUSED SQL compilation error: | Missing column specification"},
            {"CREATE MATERIALIZED VIEW m4 AS SELECT t.a, b + 1 FROM t JOIN u ON t.a = u.a",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. More than one table referenced in the view definition"},
            {"CREATE MATERIALIZED VIEW m5 AS SELECT a, b + 1 FROM vv",
                "REFUSED SQL compilation error: error line 1 at position 52 | Invalid materialized view "
                + "definition. 'VV' should be a table."},
            {"CREATE MATERIALIZED VIEW m6 AS SELECT a, SUM(b) FROM t GROUP BY a",
                "REFUSED SQL compilation error: | Missing column specification"},
            {"CREATE MATERIALIZED VIEW m7 AS SELECT a, b + 1 FROM t WHERE a IN (SELECT a FROM u)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid materialized view "
                + "definition. More than one table referenced in the view definition"},
            {"CREATE MATERIALIZED VIEW m8 AS SELECT a, RANDOM() FROM t",
                "REFUSED SQL compilation error: | Missing column specification"},
            {"CREATE MATERIALIZED VIEW m9 AS SELECT a, MEDIAN(b) FROM t GROUP BY a",
                "REFUSED SQL compilation error: | Missing column specification"},
            {"CREATE MATERIALIZED VIEW m10 AS SELECT a, COUNT(DISTINCT b) FROM t GROUP BY a",
                "REFUSED SQL compilation error: | Missing column specification"},
            {"CREATE MATERIALIZED VIEW m11 AS SELECT a, b + 1 FROM t GROUP BY ALL",
                "REFUSED SQL compilation error: Invalid materialized view definition. GROUP BY ALL not "
                + "allowed in MV definition."},
            {"CREATE MATERIALIZED VIEW m12 AS SELECT a, b + 1 FROM t HAVING a > 1",
                "REFUSED SQL compilation error: | Missing column specification"},
            {"CREATE MATERIALIZED VIEW m13 AS SELECT a + 1 FROM t AT(OFFSET => -1)",
                "REFUSED SQL compilation error: Invalid materialized view: Time travel on base table in line "
                + "1 at position 50."},
            {"CREATE MATERIALIZED VIEW m14 AS SELECT a, CURRENT_DATE() FROM t",
                "REFUSED SQL compilation error: | Missing column specification"},
            {"CREATE MATERIALIZED VIEW m15 AS SELECT a + 1 FROM t GROUP BY ROLLUP (a)",
                "REFUSED SQL compilation error: | Missing column specification"},
        });
    }
}
