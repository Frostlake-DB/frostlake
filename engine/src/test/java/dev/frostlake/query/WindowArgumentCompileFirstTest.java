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
 * An aggregate query's window calls are compiled before its select list is held to the grouping: a cast or an
 * argument the account refuses while compiling — {@code MAX(b::FLOAT) OVER ()} or an {@code ORDER BY b::FLOAT}
 * key over a BOOLEAN, {@code TRY_CAST} of a BOOLEAN to a number — answers its own sentence ahead of
 * "is not a valid group by expression", and an exact number cast to BOOLEAN is named as its comparison with
 * zero, {@code CAST(FZ.ID <> 0 AS BOOLEAN)}, in the group-by sentence (all live-verified).
 */
public class WindowArgumentCompileFirstTest extends MeasuredCellsTestSupport {

    @Test
    public void windowCastsAndTryCasts() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, d NUMBER(10,2), f FLOAT, s VARCHAR)");
        engine.execute("INSERT INTO fz VALUES (1, TRUE, 1.5, 2.5, 'x'), (0, FALSE, 0, 0, 'y')");
        assertCells(new String[][] {
            {"SELECT MAX(id), MAX(b::FLOAT) OVER () FROM fz",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS FLOAT)] for parameter "
                + "'TO_DOUBLE'"},
            {"SELECT MAX(id), MAX(id + TRY_CAST(b AS INT)) OVER () FROM fz",
                "REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types "
                + "BOOLEAN and NUMBER(38,0)"},
            {"SELECT MAX(b::FLOAT) OVER () FROM fz",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS FLOAT)] for parameter "
                + "'TO_DOUBLE'"},
            {"SELECT MAX(id + TRY_CAST(b AS INT)) OVER () FROM fz",
                "REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types "
                + "BOOLEAN and NUMBER(38,0)"},
            {"SELECT MAX(id), MAX(b::DATE) OVER () FROM fz",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS DATE)] for parameter 'TO_DATE'"},
            {"SELECT MAX(id), MAX(TRY_CAST(b AS VARCHAR)) OVER () FROM fz",
                "REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types "
                + "BOOLEAN and VARCHAR(134217728)"},
            {"SELECT MAX(id), MAX(s::FLOAT) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.S AS FLOAT)] is not a valid group by expression"},
            {"SELECT MAX(id), SUM(b) OVER () FROM fz",
                "REFUSED SQL compilation error: error line 1 at position 16 | Invalid argument types for "
                + "function 'SUM': (BOOLEAN)"},
            {"SELECT MAX(id), MAX(b::FLOAT) OVER (), MAX(id::BOOLEAN) OVER () FROM fz",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS FLOAT)] for parameter "
                + "'TO_DOUBLE'"},
            {"SELECT MAX(id), MAX(id::BOOLEAN) OVER (), MAX(b::FLOAT) OVER () FROM fz",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS FLOAT)] for parameter "
                + "'TO_DOUBLE'"},
            {"SELECT MAX(id), MAX(b::FLOAT) FROM fz",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS FLOAT)] for parameter "
                + "'TO_DOUBLE'"},
            {"SELECT MAX(id), id, MAX(b::FLOAT) OVER () FROM fz",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS FLOAT)] for parameter "
                + "'TO_DOUBLE'"},
            {"SELECT MAX(id), MAX(b::NUMBER) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0))] is not a "
                + "valid group by expression"},
            {"SELECT MAX(id), MAX(b::VARIANT::FLOAT) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(CAST(FZ.B AS VARIANT) AS FLOAT)] is not a valid group"
                + " by expression"},
            {"SELECT MAX(id), MAX((b::INT)::BOOLEAN::INT) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(CAST(CAST((CAST(CAST(FZ.B AS NUMBER(2,0)) AS "
                + "NUMBER(38,0))) <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS NUMBER(38,0))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(id::BOOLEAN) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.ID <> 0 AS BOOLEAN)] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX((id + 1)::BOOLEAN) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST((FZ.ID + 1) <> 0 AS BOOLEAN)] is not a valid group by"
                + " expression"},
            {"SELECT MAX(id), MAX(d::BOOLEAN) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.D <> (CAST(0 AS NUMBER(10,2))) AS BOOLEAN)] is not"
                + " a valid group by expression"},
            {"SELECT MAX(id), MAX(f::BOOLEAN) OVER () FROM fz",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.F AS BOOLEAN)] for parameter "
                + "'TO_BOOLEAN'"},
            {"SELECT MAX(id), MAX(s::BOOLEAN) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.S AS BOOLEAN)] is not a valid group by expression"},
            {"SELECT MAX(id), MAX(TRY_CAST(s AS BOOLEAN)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [TRY_CAST(FZ.S AS BOOLEAN)] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(CAST(id AS BOOLEAN)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.ID <> 0 AS BOOLEAN)] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(TO_BOOLEAN(id)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.ID <> 0 AS BOOLEAN)] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(b::BOOLEAN) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.B] is not a valid group by expression"},
            {"SELECT MAX(id), MAX((d * 2)::BOOLEAN) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST((FZ.D * 2) <> (CAST(0 AS NUMBER(11,2))) AS BOOLEAN)] "
                + "is not a valid group by expression"},
            {"SELECT MAX(id), MAX(TO_BOOLEAN(d)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.D <> (CAST(0 AS NUMBER(10,2))) AS BOOLEAN)] is not"
                + " a valid group by expression"},
            {"SELECT MAX(id), MAX(TO_BOOLEAN(f)) OVER () FROM fz",
                "REFUSED SQL compilation error: | invalid type [TO_BOOLEAN(FZ.F)] for parameter 'TO_BOOLEAN'"},
            {"SELECT MAX(id), MAX(id::BOOLEAN::INT) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(CAST(CAST(FZ.ID <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS "
                + "NUMBER(38,0))] is not a valid group by expression"},
            {"SELECT MAX(id), MAX(id::VARIANT::BOOLEAN) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(CAST(FZ.ID AS VARIANT) AS BOOLEAN)] is not a valid "
                + "group by expression"},
            {"SELECT MAX(id), MAX(id::DECFLOAT::BOOLEAN) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(CAST(FZ.ID AS DECFLOAT(38)) AS BOOLEAN)] is not a "
                + "valid group by expression"},
            {"SELECT MAX(id), MAX(id::BOOLEAN), MAX(id) OVER (ORDER BY id::BOOLEAN) FROM fz",
                "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression"},
        });
    }

    @Test
    public void groupedJoinedAndKeyedWindows() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, d NUMBER(10,2), f FLOAT, s VARCHAR)");
        engine.execute("INSERT INTO fz VALUES (1, TRUE, 1.5, 2.5, 'x'), (0, FALSE, 0, 0, 'y')");
        engine.execute("CREATE OR REPLACE TABLE g (k INT, v INT)");
        engine.execute("INSERT INTO g VALUES (1, 10), (0, 20)");
        assertCells(new String[][] {
            {"SELECT s, MAX(b::FLOAT) OVER () FROM fz GROUP BY s",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS FLOAT)] for parameter "
                + "'TO_DOUBLE'"},
            {"SELECT s, MAX(id), MAX(b::FLOAT) OVER () FROM fz GROUP BY s",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS FLOAT)] for parameter "
                + "'TO_DOUBLE'"},
            {"SELECT MAX(id), MAX(id) OVER (ORDER BY b::FLOAT) FROM fz",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS FLOAT)] for parameter "
                + "'TO_DOUBLE'"},
            {"SELECT MAX(id), MAX(id) OVER (PARTITION BY b::FLOAT) FROM fz",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS FLOAT)] for parameter "
                + "'TO_DOUBLE'"},
            {"SELECT MAX(fz.id), MAX(fz.b::FLOAT) OVER () FROM fz JOIN g ON fz.id = g.k",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS FLOAT)] for parameter "
                + "'TO_DOUBLE'"},
            {"SELECT MAX(fz.id), MAX(g.v + fz.id::BOOLEAN::INT) OVER () FROM fz JOIN g ON fz.id = g.k",
                "REFUSED SQL compilation error: | [G.V + (CAST(CAST(CAST(FZ.ID <> 0 AS BOOLEAN) AS "
                + "NUMBER(2,0)) AS NUMBER(38,0)))] is not a valid group by expression"},
            {"SELECT MAX(id) AS m, LAG(MAX(b::FLOAT)) OVER (ORDER BY 1) FROM fz",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS FLOAT)] for parameter "
                + "'TO_DOUBLE'"},
            {"SELECT MAX(id), MAX(id::BOOLEAN AND TRUE) OVER () FROM fz",
                "REFUSED SQL compilation error: | [(CAST(FZ.ID <> 0 AS BOOLEAN)) AND TRUE] is not a valid "
                + "group by expression"},
            {"SELECT MAX(id), MAX(-id::BOOLEAN::INT) OVER () FROM fz",
                "REFUSED SQL compilation error: | [NEGATE(CAST(CAST(CAST(FZ.ID <> 0 AS BOOLEAN) AS "
                + "NUMBER(2,0)) AS NUMBER(38,0)))] is not a valid group by expression"},
            {"SELECT MAX(id), MAX(ABS(id)::BOOLEAN) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST((ABS(FZ.ID)) <> 0 AS BOOLEAN)] is not a valid group "
                + "by expression"},
            {"SELECT MAX(id), MAX(d::NUMBER(10,2)::BOOLEAN) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST((FZ.D) <> (CAST(0 AS NUMBER(10,2))) AS BOOLEAN)] is "
                + "not a valid group by expression"},
            {"SELECT MAX(id), MAX(d::NUMBER(12,4)::BOOLEAN) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST((CAST(FZ.D AS NUMBER(12,4))) <> (CAST(0 AS "
                + "NUMBER(12,4))) AS BOOLEAN)] is not a valid group by expression"},
            {"SELECT MAX(id), MAX(id::DECFLOAT) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.ID AS DECFLOAT(38))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(id::NUMBER(38,0)::BOOLEAN) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST((FZ.ID) <> 0 AS BOOLEAN)] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(IFF(id::BOOLEAN, 1, 0)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [IFF(CAST(CAST(FZ.ID <> 0 AS BOOLEAN) AS BOOLEAN), 1, 0)] "
                + "is not a valid group by expression"},
            {"SELECT MAX(id), MAX(b), RANK() OVER (ORDER BY b::FLOAT) FROM fz",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS FLOAT)] for parameter "
                + "'TO_DOUBLE'"},
            {"SELECT COUNT(*), MAX(id), MAX(f::BOOLEAN) OVER () FROM fz",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.F AS BOOLEAN)] for parameter "
                + "'TO_BOOLEAN'"},
            {"SELECT s, id, MAX(b::FLOAT) OVER () FROM fz GROUP BY s",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS FLOAT)] for parameter "
                + "'TO_DOUBLE'"},
            {"SELECT s, MAX(b::FLOAT) OVER () FROM fz GROUP BY ALL",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS FLOAT)] for parameter "
                + "'TO_DOUBLE'"},
            {"SELECT MAX(id), MAX(nosuch) OVER () FROM fz",
                "REFUSED SQL compilation error: error line 1 at position 20 | invalid identifier 'NOSUCH'"},
            {"SELECT id, MAX(b::FLOAT) FROM fz",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS FLOAT)] for parameter "
                + "'TO_DOUBLE'"},
            {"SELECT id, MAX(b::FLOAT) OVER () FROM fz HAVING COUNT(*) > 0",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS FLOAT)] for parameter "
                + "'TO_DOUBLE'"},
        });
    }

    @Test
    public void windowKeys() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, d NUMBER(10,2), f FLOAT, s VARCHAR)");
        engine.execute("INSERT INTO fz VALUES (1, TRUE, 1.5, 2.5, 'x'), (0, FALSE, 0, 0, 'y')");
        engine.execute("CREATE OR REPLACE TABLE g (k INT, v INT)");
        engine.execute("INSERT INTO g VALUES (1, 10), (0, 20)");
        assertCells(new String[][] {
            {"SELECT MAX(id), MAX(id) OVER (ORDER BY nosuch) FROM fz",
                "REFUSED SQL compilation error: error line 1 at position 39 | invalid identifier 'NOSUCH'"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY MAX(b::FLOAT)) FROM fz",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS FLOAT)] for parameter "
                + "'TO_DOUBLE'"},
            {"SELECT s, RANK() OVER (PARTITION BY s ORDER BY MAX(b::FLOAT)) FROM fz GROUP BY s",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS FLOAT)] for parameter "
                + "'TO_DOUBLE'"},
            {"SELECT s, RANK() OVER (ORDER BY s::FLOAT) FROM fz GROUP BY s",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY id) FROM fz",
                "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY id + 'a') FROM fz",
                "REFUSED SQL compilation error: | [(CAST(FZ.ID AS NUMBER(38,5))) + (TO_NUMBER('a', 38, 5))] "
                + "is not a valid group by expression"},
            {"SELECT MAX(id), MAX(id) OVER (PARTITION BY b, b::FLOAT) FROM fz",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS FLOAT)] for parameter "
                + "'TO_DOUBLE'"},
            {"SELECT MAX(id), MAX(id) OVER (ORDER BY b::FLOAT ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT"
                + " ROW) FROM fz",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS FLOAT)] for parameter "
                + "'TO_DOUBLE'"},
            {"SELECT fz.s, MAX(g.v), RANK() OVER (ORDER BY fz.b::FLOAT) FROM fz JOIN g ON fz.id = g.k "
                + "GROUP BY fz.s",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS FLOAT)] for parameter "
                + "'TO_DOUBLE'"},
            {"SELECT MAX(id), MAX(id) OVER (ORDER BY TO_BOOLEAN(f)) FROM fz",
                "REFUSED SQL compilation error: | invalid type [TO_BOOLEAN(FZ.F)] for parameter 'TO_BOOLEAN'"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY nosuch(id)) FROM fz",
                "REFUSED SQL compilation error: | Unknown function NOSUCH."},
        });
    }

    @Test
    public void decfloatCasts() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, x DECFLOAT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (1, 1, TRUE), (0, 0, FALSE)");
        assertCells(new String[][] {
            {"SELECT TO_DATE(id::DECFLOAT) FROM fz",
                "REFUSED SQL compilation error: | invalid type [TO_DATE(TO_DECFLOAT(FZ.ID))] for parameter "
                + "'TO_DATE'"},
            {"SELECT TO_DATE(x::NUMBER(38,0)) FROM fz",
                "REFUSED SQL compilation error: | invalid type [TO_DATE(TO_NUMBER(FZ.X))] for parameter "
                + "'TO_DATE'"},
            {"SELECT MAX(id), MAX(x::INT) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.X AS NUMBER(38,0))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(x::BOOLEAN) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.X AS BOOLEAN)] is not a valid group by expression"},
            {"SELECT MAX(id), MAX(x::DECFLOAT) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.X] is not a valid group by expression"},
            {"SELECT b::DATE, id::DECFLOAT::DATE FROM fz",
                "REFUSED SQL compilation error: | invalid type [CAST(FZ.B AS DATE)] for parameter 'TO_DATE'"},
            {"SELECT MAX(id), MAX(TO_BOOLEAN(x)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.X AS BOOLEAN)] is not a valid group by expression"},
            {"SELECT TO_DATE(id::DECFLOAT::INT) FROM fz",
                "REFUSED SQL compilation error: | invalid type [TO_DATE(TO_NUMBER(TO_DECFLOAT(FZ.ID)))] for "
                + "parameter 'TO_DATE'"},
            {"SELECT TO_DATE(x::NUMBER(10,2)) FROM fz",
                "REFUSED SQL compilation error: | invalid type [TO_DATE(TO_NUMBER(FZ.X))] for parameter "
                + "'TO_DATE'"},
            {"SELECT MAX(id), MAX(x::NUMBER(10,2)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.X AS NUMBER(10,2))] is not a valid group by "
                + "expression"},
            {"SELECT TO_DATE(TO_BOOLEAN(x)) FROM fz",
                "REFUSED SQL compilation error: | invalid type [TO_DATE(TO_BOOLEAN(FZ.X))] for parameter "
                + "'TO_DATE'"},
        });
    }
}
