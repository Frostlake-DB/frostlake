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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * FIRST_VALUE, LAST_VALUE, NTH_VALUE, LAG and LEAD over a partition of twenty thousand rows, with function-call
 * arguments, explicit frames, peers and IGNORE NULLS over long runs of NULLs, each answer read as a digest of every
 * row's value; and the argument evaluated on every row of the partition, those no frame reaches included
 * (live-verified).
 */
public class WindowValueFunctionCellsTest extends MeasuredCellsTestSupport {

    @Test
    public void largePartitionValueFunctions() {
        engine.execute("CREATE TABLE t AS SELECT id, MOD(id, 3) g, FLOOR(id / 10) k, IFF(MOD(id, 7) = 0 OR id "
            + "BETWEEN 5000 AND 6200, NULL, MOD(id * 37, 1000)) w, IFF(MOD(id, 5) = 0 OR id BETWEEN 9000 "
            + "AND 9500, NULL, 'v' || MOD(id * 13, 97)) s FROM (SELECT ROW_NUMBER() OVER (ORDER BY SEQ4()) "
            + "id FROM TABLE(GENERATOR(ROWCOUNT => 20000)))");
        engine.execute("CREATE TABLE t5 AS SELECT column1 id FROM VALUES (1),(2),(3),(4),(5)");
        assertCells(new String[][] {
            {"SELECT MD5(COUNT(f) || '/' || LISTAGG(COALESCE(f::VARCHAR, '~'), ',') WITHIN GROUP (ORDER BY"
                + " id)) FROM (SELECT id, FIRST_VALUE(ABS(w)) OVER (ORDER BY id) f FROM t)",
                "a04e3611491ae4e291c23015fbe2aa1a"},
            {"SELECT MD5(COUNT(f) || '/' || LISTAGG(COALESCE(f::VARCHAR, '~'), ',') WITHIN GROUP (ORDER BY"
                + " id)) FROM (SELECT id, FIRST_VALUE(ABS(w)) IGNORE NULLS OVER (ORDER BY id ROWS BETWEEN 3 "
                + "PRECEDING AND 2 FOLLOWING) f FROM t)",
                "ada781d9d0f777bd7985299ade75c0c4"},
            {"SELECT MD5(COUNT(f) || '/' || LISTAGG(COALESCE(f::VARCHAR, '~'), ',') WITHIN GROUP (ORDER BY"
                + " id)) FROM (SELECT id, FIRST_VALUE(ABS(w)) IGNORE NULLS OVER (PARTITION BY g ORDER BY id "
                + "ROWS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING) f FROM t)",
                "c237713488f4bf88b4e9963abee9a257"},
            {"SELECT MD5(COUNT(f) || '/' || LISTAGG(COALESCE(f::VARCHAR, '~'), ',') WITHIN GROUP (ORDER BY"
                + " id)) FROM (SELECT id, FIRST_VALUE(ABS(w)) OVER (ORDER BY k RANGE BETWEEN UNBOUNDED "
                + "PRECEDING AND CURRENT ROW) f FROM t)",
                "a04e3611491ae4e291c23015fbe2aa1a"},
            {"SELECT MD5(COUNT(f) || '/' || LISTAGG(COALESCE(f::VARCHAR, '~'), ',') WITHIN GROUP (ORDER BY"
                + " id)) FROM (SELECT id, FIRST_VALUE(w) OVER (ORDER BY id ROWS BETWEEN 2 FOLLOWING AND 5 "
                + "FOLLOWING) f FROM t)",
                "42e569ca91e1a7b3e6d82657751889bf"},
            {"SELECT MD5(COUNT(f) || '/' || LISTAGG(COALESCE(f::VARCHAR, '~'), ',') WITHIN GROUP (ORDER BY"
                + " id)) FROM (SELECT id, FIRST_VALUE(UPPER(s)) IGNORE NULLS OVER (ORDER BY id ROWS BETWEEN 1 "
                + "PRECEDING AND 1 FOLLOWING) f FROM t)",
                "777f61eb249ed61f2c70bf2e8c35a533"},
            {"SELECT MD5(COUNT(f) || '/' || LISTAGG(COALESCE(f::VARCHAR, '~'), ',') WITHIN GROUP (ORDER BY"
                + " id)) FROM (SELECT id, LAST_VALUE(UPPER(s)) OVER (ORDER BY id) f FROM t)",
                "b374578a0558cfbe51790590b41a8653"},
            {"SELECT MD5(COUNT(f) || '/' || LISTAGG(COALESCE(f::VARCHAR, '~'), ',') WITHIN GROUP (ORDER BY"
                + " id)) FROM (SELECT id, LAST_VALUE(ABS(w)) IGNORE NULLS OVER (ORDER BY id ROWS UNBOUNDED "
                + "PRECEDING) f FROM t)",
                "d4f1d1783f20d365b7ddc3f6a49e5901"},
            {"SELECT MD5(COUNT(f) || '/' || LISTAGG(COALESCE(f::VARCHAR, '~'), ',') WITHIN GROUP (ORDER BY"
                + " id)) FROM (SELECT id, LAST_VALUE(NULLIF(w, w)) IGNORE NULLS OVER (ORDER BY id ROWS "
                + "UNBOUNDED PRECEDING) f FROM t)",
                "b374578a0558cfbe51790590b41a8653"},
            {"SELECT MD5(COUNT(f) || '/' || LISTAGG(COALESCE(f::VARCHAR, '~'), ',') WITHIN GROUP (ORDER BY"
                + " id)) FROM (SELECT id, LAST_VALUE(ABS(w)) OVER (PARTITION BY g ORDER BY id ROWS BETWEEN 4 "
                + "PRECEDING AND 1 PRECEDING) f FROM t)",
                "b2a87bc338c82d56eb26f5fe1a426ee2"},
            {"SELECT MD5(COUNT(f) || '/' || LISTAGG(COALESCE(f::VARCHAR, '~'), ',') WITHIN GROUP (ORDER BY"
                + " id)) FROM (SELECT id, NTH_VALUE(UPPER(s), 2) OVER (ORDER BY id) f FROM t)",
                "8077b8dcb7f38d2c909476ec8de14174"},
            {"SELECT MD5(COUNT(f) || '/' || LISTAGG(COALESCE(f::VARCHAR, '~'), ',') WITHIN GROUP (ORDER BY"
                + " id)) FROM (SELECT id, NTH_VALUE(ABS(w), 3) IGNORE NULLS OVER (ORDER BY id ROWS BETWEEN 10 "
                + "PRECEDING AND CURRENT ROW) f FROM t)",
                "0fc023683863e7d46b8dad08dbc9d911"},
            {"SELECT MD5(COUNT(f) || '/' || LISTAGG(COALESCE(f::VARCHAR, '~'), ',') WITHIN GROUP (ORDER BY"
                + " id)) FROM (SELECT id, NTH_VALUE(ABS(w), 1) IGNORE NULLS OVER (ORDER BY id ROWS BETWEEN "
                + "UNBOUNDED PRECEDING AND CURRENT ROW) f FROM t)",
                "a04e3611491ae4e291c23015fbe2aa1a"},
            {"SELECT MD5(COUNT(f) || '/' || LISTAGG(COALESCE(f::VARCHAR, '~'), ',') WITHIN GROUP (ORDER BY"
                + " id)) FROM (SELECT id, NTH_VALUE(ABS(w), 30) OVER (ORDER BY id ROWS BETWEEN 10 PRECEDING AND"
                + " CURRENT ROW) f FROM t)",
                "b374578a0558cfbe51790590b41a8653"},
            {"SELECT MD5(COUNT(f) || '/' || LISTAGG(COALESCE(f::VARCHAR, '~'), ',') WITHIN GROUP (ORDER BY"
                + " id)) FROM (SELECT id, NTH_VALUE(UPPER(s), 3) FROM LAST IGNORE NULLS OVER (ORDER BY id) f "
                + "FROM t)",
                "ac3c1d758cbdce662adef6b8d08262a9"},
            {"SELECT MD5(COUNT(f) || '/' || LISTAGG(COALESCE(f::VARCHAR, '~'), ',') WITHIN GROUP (ORDER BY"
                + " id)) FROM (SELECT id, LAG(ABS(w)) IGNORE NULLS OVER (ORDER BY id) f FROM t)",
                "89fbbab1067b0df43731903b17c75211"},
            {"SELECT MD5(COUNT(f) || '/' || LISTAGG(COALESCE(f::VARCHAR, '~'), ',') WITHIN GROUP (ORDER BY"
                + " id)) FROM (SELECT id, LAG(ABS(w), 3, -1) IGNORE NULLS OVER (ORDER BY id) f FROM t)",
                "18cab95465247b60bb8e99aa72d0fa10"},
            {"SELECT MD5(COUNT(f) || '/' || LISTAGG(COALESCE(f::VARCHAR, '~'), ',') WITHIN GROUP (ORDER BY"
                + " id)) FROM (SELECT id, LAG(NULLIF(w, w)) IGNORE NULLS OVER (ORDER BY id) f FROM t)",
                "b374578a0558cfbe51790590b41a8653"},
            {"SELECT MD5(COUNT(f) || '/' || LISTAGG(COALESCE(f::VARCHAR, '~'), ',') WITHIN GROUP (ORDER BY"
                + " id)) FROM (SELECT id, LAG(ABS(w), -2) IGNORE NULLS OVER (PARTITION BY g ORDER BY id) f FROM"
                + " t)",
                "1e5cbe182447c988c870603e0cf46a62"},
            {"SELECT MD5(COUNT(f) || '/' || LISTAGG(COALESCE(f::VARCHAR, '~'), ',') WITHIN GROUP (ORDER BY"
                + " id)) FROM (SELECT id, LAG(UPPER(s), 2) OVER (ORDER BY id) f FROM t)",
                "54b3bcff67e9f322beabb1c7ac2d05a7"},
            {"SELECT MD5(COUNT(f) || '/' || LISTAGG(COALESCE(f::VARCHAR, '~'), ',') WITHIN GROUP (ORDER BY"
                + " id)) FROM (SELECT id, LEAD(ABS(w), 2) IGNORE NULLS OVER (ORDER BY id) f FROM t)",
                "e808fad77ac0d273f86ce5095ad314b6"},
            {"SELECT MD5(COUNT(f) || '/' || LISTAGG(COALESCE(f::VARCHAR, '~'), ',') WITHIN GROUP (ORDER BY"
                + " id)) FROM (SELECT id, LEAD(UPPER(s)) IGNORE NULLS OVER (PARTITION BY g ORDER BY id) f FROM "
                + "t)",
                "b3dc6e25681bb5bdbc1aa7009e246078"},
            {"SELECT MD5(COUNT(f) || '/' || LISTAGG(COALESCE(f::VARCHAR, '~'), ',') WITHIN GROUP (ORDER BY"
                + " id)) FROM (SELECT id, LEAD(ABS(w), 1, 0) IGNORE NULLS OVER (ORDER BY id DESC) f FROM t)",
                "f5847da56f808a394240abeafe14aa36"},
            {"SELECT COUNT(DISTINCT f) FROM (SELECT FIRST_VALUE(RANDOM()) OVER (ORDER BY id) f FROM t5)",
                "1"},
            {"SELECT COUNT(DISTINCT f) FROM (SELECT LAST_VALUE(UUID_STRING()) OVER (ORDER BY id) f FROM "
                + "t5)",
                "1"},
            {"SELECT COUNT(DISTINCT f) FROM (SELECT LAG(RANDOM(), 1, 0) IGNORE NULLS OVER (PARTITION BY 1 "
                + "ORDER BY id DESC) f FROM t5 WHERE id > 3)",
                "2"},
            {"SELECT id, LAG(1 / (id - 5)) OVER (ORDER BY id) FROM t5 ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id, FIRST_VALUE(1 / (id - 5)) OVER (ORDER BY id ROWS BETWEEN 1 PRECEDING AND 1 "
                + "PRECEDING) FROM t5 ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id, LAG(1 / (id - 5)) IGNORE NULLS OVER (ORDER BY id) FROM t5 ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id, NTH_VALUE(1 / (id - 5), 2) OVER (ORDER BY id) FROM t5 ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id, FIRST_VALUE(SQRT(id - 5)) OVER (ORDER BY id ROWS BETWEEN 1 FOLLOWING AND 1 FOLLOWING) "
                + "FROM t5 ORDER BY id",
                "REFUSED Invalid floating point operation: sqrt(-4)"},
        });
    }
}
