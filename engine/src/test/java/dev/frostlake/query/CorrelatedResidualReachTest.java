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
 * Which inner rows a correlated subquery reading the outer row through an equality computes, and so which row faults
 * refuse the statement (live-verified). A residual's columns and an aggregate's arguments are computed over every inner
 * row where the outer table has more rows than the inner relation, and over the rows of the keys it holds where it has
 * fewer; a filter on the outer key by constants, beside the subquery in the WHERE or in the WHERE of the query whose
 * select item holds it, bounds the inner key the same way, and a NOT IN beside in the WHERE reads the held keys. An
 * IN's members beside a residual are computed over every inner row whatever the sizes, bounded only by a key filter
 * beside it in the WHERE, while a NOT IN's member is computed only for the rows whose residual holds. A residual
 * conjunct that no row of the inner relation satisfies beside any row of the outer table has no inner row read at all,
 * so no fault in the other columns is met.
 */
public class CorrelatedResidualReachTest extends MeasuredCellsTestSupport {

    @Test
    public void faultsFollowTheRowsTheInnerSideReads() {
        engine.execute("CREATE OR REPLACE TABLE ka(id INT, v INT, w INT, s VARCHAR) AS SELECT * FROM VALUES "
            + "(1,1,10,'1'),(2,5,20,'2'),(3,0,30,'x'),(NULL,1,10,NULL),(4,2,NULL,'4'),(5,0,30,'5.0')");
        engine.execute("CREATE OR REPLACE TABLE kz(k INT, j INT, z INT, t VARCHAR) AS SELECT * FROM VALUES "
            + "(1,5,1,'1'),(2,25,5,'2'),(9,5,0,'x')");
        engine.execute("CREATE OR REPLACE TABLE ky(k INT, j INT, z INT, t VARCHAR) AS SELECT * FROM VALUES "
            + "(1,5,1,'1'),(2,25,0,'x'),(9,5,2,'9')");
        engine.execute("CREATE OR REPLACE TABLE ka(id INT, v INT, w INT, s VARCHAR) AS SELECT * FROM VALUES "
            + "(1,1,10,'1'),(2,5,20,'2'),(3,0,30,'x'),(NULL,1,10,NULL),(4,2,NULL,'4'),(5,0,30,'5.0')");
        engine.execute("CREATE OR REPLACE TABLE ky3(k INT, j INT, z INT) AS SELECT * FROM VALUES "
            + "(1,5,1),(2,3,0),(9,5,2)");
        engine.execute("CREATE OR REPLACE TABLE ky(k INT, j INT, z INT, t VARCHAR) AS SELECT * FROM VALUES "
            + "(1,5,1,'1'),(2,25,0,'x'),(9,5,2,'9')");
        engine.execute("CREATE OR REPLACE TABLE ks(id INT, v INT, w INT) AS SELECT * FROM VALUES (1,1,10),(2,5,20)");
        engine.execute("CREATE OR REPLACE TABLE kq(k INT, j INT, z INT) AS SELECT * FROM VALUES (1,5,1),(9,5,0)");
        engine.execute("CREATE OR REPLACE TABLE kz(k INT, j INT, z INT, t VARCHAR) AS SELECT * FROM VALUES "
            + "(1,5,1,'1'),(2,25,5,'2'),(9,5,0,'x')");
        assertCells(new String[][] {
            {"SELECT id, w IN (SELECT 10 / b.z FROM kz b WHERE b.k = a.id AND b.j > a.v) FROM ka a ORDER "
                + "BY id",
                "REFUSED Division by zero"},
            {"SELECT id, w IN (SELECT b.t::INT FROM kz b WHERE b.k = a.id AND b.j > a.v) FROM ka a ORDER "
                + "BY id",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT id FROM ka a WHERE w IN (SELECT 10 / b.z FROM kz b WHERE b.k = a.id AND b.j > a.v) "
                + "ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id, w NOT IN (SELECT 10 / b.z FROM ky b WHERE b.k = a.id AND b.j > a.v) FROM ka a "
                + "ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id, w IN (SELECT b.j FROM kz b WHERE b.k = a.id AND b.j / b.z > a.v) FROM ka a ORDER "
                + "BY id",
                "REFUSED Division by zero"},
            {"SELECT id, w NOT IN (SELECT b.j FROM kz b WHERE b.k = a.id AND b.j / b.z > a.v) FROM ka a "
                + "ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id, EXISTS (SELECT 1 FROM kz b WHERE b.k = a.id AND b.j / b.z > a.v) FROM ka a ORDER "
                + "BY id",
                "REFUSED Division by zero"},
            {"SELECT id, NOT EXISTS (SELECT 1 FROM kz b WHERE b.k = a.id AND b.j / b.z > a.v) FROM ka a "
                + "ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id FROM ka a WHERE a.id = 1 AND w IN (SELECT b.j FROM kz b WHERE b.k = a.id AND b.j /"
                + " b.z > a.v)",
                ""},
            {"SELECT id FROM ka a WHERE a.id = 1 AND w IN (SELECT 10 / b.z FROM kz b WHERE b.k = a.id AND "
                + "b.j > a.v)",
                "1"},
            {"SELECT id FROM ka a WHERE a.id IN (1, 2) AND w IN (SELECT 10 / b.z FROM kz b WHERE b.k = "
                + "a.id AND b.j > a.v)",
                "1"},
            {"SELECT id FROM ka a WHERE a.id BETWEEN 1 AND 2 AND w IN (SELECT 10 / b.z FROM kz b WHERE b.k"
                + " = a.id AND b.j > a.v)",
                "1"},
            {"SELECT id FROM ka a WHERE a.v = 1 AND w IN (SELECT 10 / b.z FROM kz b WHERE b.k = a.id AND "
                + "b.j > a.v)",
                "REFUSED Division by zero"},
            {"SELECT id FROM ka a WHERE a.id = 1 AND w NOT IN (SELECT b.j FROM kz b WHERE b.k = a.id AND "
                + "b.j / b.z > a.v)",
                "1"},
            {"SELECT id FROM ka a WHERE a.id = 1 OR w IN (SELECT 10 / b.z FROM kz b WHERE b.k = a.id AND "
                + "b.j > a.v) ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id FROM ka a WHERE a.id < 3 AND w IN (SELECT 10 / b.z FROM kz b WHERE b.k = a.id AND "
                + "b.j > a.v)",
                "1"},
            {"SELECT id, w IN (SELECT 10 / b.z FROM kz b WHERE b.k = a.id AND b.j > a.v) FROM ka a WHERE "
                + "id = 1",
                "REFUSED Division by zero"},
            {"SELECT id, w IN (SELECT 10 / b.z FROM kz b WHERE b.k = a.id AND b.j > a.v) FROM (SELECT * "
                + "FROM ka WHERE id = 1) a",
                "REFUSED Division by zero"},
            {"SELECT id, w IN (SELECT b.j FROM kz b WHERE b.k = a.id AND b.j / b.z > a.v) FROM ka a WHERE "
                + "id = 99",
                ""},
            {"SELECT id, w IN (SELECT 10 / b.z FROM kz b WHERE b.k = a.id AND b.j > a.v) FROM ka a WHERE "
                + "id = 99",
                ""},
            {"SELECT id, (SELECT MAX(b.j) FROM kz b WHERE b.k = a.id AND b.j / b.z > a.v) FROM ka a ORDER "
                + "BY id",
                "REFUSED Division by zero"},
            {"SELECT id, (SELECT MAX(10 / b.z) FROM kz b WHERE b.k = a.id AND b.j > a.v) FROM ka a ORDER "
                + "BY id",
                "REFUSED Division by zero"},
            {"SELECT id, (SELECT MAX(b.t::INT) FROM kz b WHERE b.k = a.id AND b.j > a.v) FROM ka a ORDER "
                + "BY id",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT id, (SELECT SUM(10 / b.z) FROM kz b WHERE b.k = a.id AND b.j > a.v) FROM ka a ORDER "
                + "BY id",
                "REFUSED Division by zero"},
            {"SELECT id, (SELECT COUNT(10 / b.z) FROM kz b WHERE b.k = a.id AND b.j > a.v) FROM ka a ORDER"
                + " BY id",
                "REFUSED Division by zero"},
            {"SELECT id FROM ka a WHERE a.id = 1 AND a.v < (SELECT MAX(10 / b.z) FROM kz b WHERE b.k = "
                + "a.id AND b.j > a.v)",
                "1"},
            {"SELECT id, (SELECT MAX(10 / b.z) + 1 FROM kz b WHERE b.k = a.id AND b.j > a.v) FROM ka a "
                + "ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id, (SELECT MAX(10 / b.z) FROM kz b WHERE b.k = a.id) FROM ka a ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id, 10 IN (SELECT 10 / b.z FROM kz b WHERE b.k = a.id) FROM ka a ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id, 10 NOT IN (SELECT 10 / b.z FROM kz b WHERE b.k = a.id) FROM ka a ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id, w IN (SELECT 10 / b.z FROM ky3 b WHERE b.k = a.id AND b.j > a.v) FROM ka a ORDER "
                + "BY id",
                "REFUSED Division by zero"},
            {"SELECT id, w IN (SELECT 10 / b.z FROM ky3 b WHERE b.k = a.id AND b.j > a.v) FROM ks a ORDER "
                + "BY id",
                "REFUSED Division by zero"},
            {"SELECT id, (SELECT MAX(10 / b.z) FROM kq b WHERE b.k = a.id AND b.j > a.v) FROM ks a ORDER "
                + "BY id",
                "REFUSED Division by zero"},
            {"SELECT id FROM ks a WHERE EXISTS (SELECT 1 FROM kq b WHERE b.k = a.id AND b.j / b.z > a.v) "
                + "ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id FROM ks a WHERE w IN (SELECT b.j FROM kq b WHERE b.k = a.id AND b.j / b.z > a.v) "
                + "ORDER BY id",
                ""},
            {"SELECT id FROM ks a WHERE EXISTS (SELECT 1 FROM kz b WHERE b.k = a.id AND b.j / b.z > a.v) "
                + "ORDER BY id",
                "1"},
            {"SELECT id FROM ks a WHERE w IN (SELECT b.j FROM kz b WHERE b.k = a.id AND b.j / b.z > a.v) "
                + "ORDER BY id",
                ""},
            {"SELECT id FROM ks a WHERE a.v < (SELECT MAX(10 / b.z) FROM kz b WHERE b.k = a.id AND b.j > "
                + "a.v) ORDER BY id",
                "1"},
            {"SELECT id FROM ka a WHERE a.v < (SELECT MAX(10 / b.z) FROM kz b WHERE b.k = a.id AND b.j > "
                + "a.v) ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id FROM ka a WHERE a.id = 1 AND a.v < (SELECT MAX(10 / b.z) FROM kz b WHERE b.k = "
                + "a.id AND b.j > a.v) ORDER BY id",
                "1"},
        });
    }

    @Test
    public void sizesAndFiltersBoundTheInnerRows() {
        engine.execute("CREATE OR REPLACE TABLE ka(id INT, v INT, w INT, s VARCHAR) AS SELECT * FROM VALUES "
            + "(1,1,10,'1'),(2,5,20,'2'),(3,0,30,'x'),(NULL,1,10,NULL),(4,2,NULL,'4'),(5,0,30,'5.0')");
        engine.execute("CREATE OR REPLACE TABLE ks(id INT, v INT, w INT) AS SELECT * FROM VALUES (1,1,10),(2,5,20)");
        engine.execute("CREATE OR REPLACE TABLE kz(k INT, j INT, z INT, t VARCHAR) AS SELECT * FROM VALUES "
            + "(1,5,1,'1'),(2,25,5,'2'),(9,5,0,'x')");
        engine.execute("CREATE OR REPLACE TABLE kw(k INT, j INT, z INT, t VARCHAR) AS SELECT * FROM VALUES "
            + "(1,5,1,'1'),(2,25,5,'2'),(9,5,0,'x'),(11,1,1,'1'),(12,1,1,'1'),(13,1,1,'1'),(14,1,1,'1'),(15,1,1,'1'),(16,1,1,'1'),(17,1,1,'1')");
        engine.execute("CREATE OR REPLACE TABLE k1(id INT, v INT, w INT) AS SELECT * FROM VALUES (1,1,10)");
        engine.execute("CREATE OR REPLACE TABLE ka(id INT, v INT, w INT, s VARCHAR) AS SELECT * FROM VALUES "
            + "(1,1,10,'1'),(2,5,20,'2'),(3,0,30,'x'),(NULL,1,10,NULL),(4,2,NULL,'4'),(5,0,30,'5.0')");
        engine.execute("CREATE OR REPLACE TABLE ks(id INT, v INT, w INT) AS SELECT * FROM VALUES (1,1,10),(2,5,20)");
        engine.execute("CREATE OR REPLACE TABLE kz(k INT, j INT, z INT, t VARCHAR) AS SELECT * FROM VALUES "
            + "(1,5,1,'1'),(2,25,5,'2'),(9,5,0,'x')");
        engine.execute("CREATE OR REPLACE TABLE kz2(k INT, k2 INT, j INT, z INT) AS SELECT * FROM VALUES "
            + "(1,1,5,1),(2,1,25,5),(9,1,5,0)");
        assertCells(new String[][] {
            {"SELECT id, w IN (SELECT 10 / b.z FROM kz b WHERE b.k = a.id AND b.j > a.v) FROM ks a ORDER "
                + "BY id",
                "REFUSED Division by zero"},
            {"SELECT id FROM ks a WHERE w IN (SELECT 10 / b.z FROM kz b WHERE b.k = a.id AND b.j > a.v) "
                + "ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id FROM ks a WHERE NOT EXISTS (SELECT 1 FROM kz b WHERE b.k = a.id AND b.j / b.z > "
                + "a.v) ORDER BY id",
                "2"},
            {"SELECT id, w IN (SELECT 10 / b.z FROM kw b WHERE b.k = a.id AND b.j > a.v) FROM ka a ORDER "
                + "BY id",
                "REFUSED Division by zero"},
            {"SELECT id FROM ka a WHERE w IN (SELECT 10 / b.z FROM kw b WHERE b.k = a.id AND b.j > a.v) "
                + "ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id, w IN (SELECT 10 / b.z FROM kz b WHERE b.k = a.id AND b.j > a.v) FROM ka a WHERE "
                + "id < 3 ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id FROM ka a WHERE id = 1 AND NOT EXISTS (SELECT 1 FROM kz b WHERE b.k = a.id AND b.j"
                + " / b.z > a.v)",
                ""},
            {"SELECT a.id FROM ka a WHERE a.id = 1 AND w IN (SELECT b.j FROM kz b WHERE a.id = b.k AND b.j"
                + " / b.z > a.v)",
                ""},
            {"SELECT a.id FROM ka a WHERE 1 = a.id AND w IN (SELECT b.j FROM kz b WHERE b.k = a.id AND b.j"
                + " / b.z > a.v)",
                ""},
            {"SELECT id FROM ka a WHERE a.id = 1 AND 10 IN (SELECT 10 / b.z FROM kz b WHERE b.k = a.id)",
                "1"},
            {"SELECT id FROM ka a WHERE 10 IN (SELECT 10 / b.z FROM kz b WHERE b.k = a.id) ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id FROM ks a WHERE 10 IN (SELECT 10 / b.z FROM kz b WHERE b.k = a.id) ORDER BY id",
                "1"},
            {"SELECT id FROM ka a WHERE a.id = 2 AND a.v < (SELECT MAX(10 / b.z) FROM kz b WHERE b.k = "
                + "a.id)",
                ""},
            {"SELECT id FROM ka a WHERE a.v < (SELECT MAX(10 / b.z) FROM kz b WHERE b.k = a.id) ORDER BY "
                + "id",
                "REFUSED Division by zero"},
            {"SELECT id FROM ks a WHERE a.v < (SELECT MAX(10 / b.z) FROM kz b WHERE b.k = a.id) ORDER BY "
                + "id",
                "1"},
            {"SELECT id, w IN (SELECT b.j FROM kz2 b WHERE b.k = a.id AND b.k2 = 1 AND b.j / b.z > a.v) "
                + "FROM ka a ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id, (SELECT MAX(b.j) FROM kz2 b WHERE b.k = a.id AND b.k2 = a.v AND b.j / b.z > a.w) "
                + "FROM ka a ORDER BY id",
                "REFUSED Division by zero"},
            {"SELECT id FROM ka a WHERE a.id BETWEEN 1 AND 2 AND a.v < (SELECT MAX(10 / b.z) FROM kz b "
                + "WHERE b.k = a.id AND b.j > a.v) ORDER BY id",
                "1"},
            {"SELECT id, w IN (SELECT b.t::INT FROM kz b WHERE b.k = a.id AND b.j > a.v) FROM ka a WHERE "
                + "id = 99",
                ""},
        });
    }

    @Test
    public void aResidualNoRowPairSatisfiesReadsNoInnerRow() {
        engine.execute("CREATE OR REPLACE TABLE ka(id INT, v INT, w INT, s VARCHAR) AS SELECT * FROM VALUES "
            + "(1,1,10,'1'),(2,5,20,'2'),(3,0,30,'x'),(NULL,1,10,NULL),(4,2,NULL,'4'),(5,0,30,'5.0')");
        engine.execute("CREATE OR REPLACE TABLE ky(k INT, j INT, z INT, t VARCHAR) AS SELECT * FROM VALUES "
            + "(1,5,1,'1'),(2,25,0,'x'),(9,5,2,'9')");
        engine.execute("CREATE OR REPLACE TABLE ky5(k INT, j INT, z INT) AS SELECT * FROM VALUES "
            + "(1,5,1),(2,25,0),(9,50,2)");
        engine.execute("CREATE OR REPLACE TABLE ky7(k INT, j INT, z INT) AS SELECT * FROM VALUES "
            + "(1,1,1),(2,3,0),(9,1,2)");
        engine.execute("CREATE OR REPLACE TABLE kp(k INT, j INT, j2 INT, z INT) AS SELECT * FROM VALUES "
            + "(1,4,15,1),(2,0,0,0)");
        assertCells(new String[][] {
            {"SELECT COUNT_IF(w IN (SELECT 10 / b.z FROM ky b WHERE b.k = a.id AND b.j > a.v + 100)) FROM "
                + "ka a",
                "0"},
            {"SELECT COUNT_IF(w IN (SELECT 10 / b.z FROM ky b WHERE b.k = a.id AND b.j > a.v + 30)) FROM "
                + "ka a",
                "0"},
            {"SELECT COUNT(*) FROM ka a WHERE w IN (SELECT 10 / b.z FROM ky b WHERE b.k = a.id AND b.j > "
                + "a.v + 100)",
                "0"},
            {"SELECT COUNT(*) FROM ka a WHERE w NOT IN (SELECT 10 / b.z FROM ky b WHERE b.k = a.id AND b.j"
                + " > a.v + 100)",
                "6"},
            {"SELECT COUNT((SELECT MAX(10 / b.z) FROM ky b WHERE b.k = a.id AND b.j > a.v + 100)) FROM ka "
                + "a",
                "0"},
            {"SELECT COUNT((SELECT SUM(10 / b.z) FROM ky b WHERE b.k = a.id AND b.j > a.v + 100)) FROM ka "
                + "a",
                "0"},
            {"SELECT COUNT_IF(w IN (SELECT b.t::INT FROM ky b WHERE b.k = a.id AND b.j > a.v + 100)) FROM "
                + "ka a",
                "0"},
            {"SELECT COUNT_IF(w IN (SELECT 10 / b.z FROM ky b WHERE b.k = a.id AND b.j > a.v + 100)) FROM "
                + "(SELECT * FROM ka WHERE id < 3) a",
                "0"},
            {"SELECT COUNT(*) FROM ka a WHERE a.id = 2 AND w IN (SELECT 10 / b.z FROM ky b WHERE b.k = "
                + "a.id AND b.j > a.v + 100)",
                "0"},
            {"SELECT COUNT_IF(EXISTS (SELECT 1 FROM ky b WHERE b.k = a.id AND b.j > a.v + 100 AND 10 / b.z"
                + " > 0)) FROM ka a",
                "0"},
            {"SELECT COUNT_IF(EXISTS (SELECT 1 FROM ky b WHERE b.k = a.id AND b.j > a.v + 100 AND 10 / b.z"
                + " > a.v)) FROM ka a",
                "0"},
            {"SELECT COUNT(*) FROM ka a WHERE EXISTS (SELECT 1 FROM ky b WHERE b.k = a.id AND b.j > a.v + "
                + "100 AND 10 / b.z > a.v)",
                "0"},
            {"SELECT COUNT_IF(NOT EXISTS (SELECT 1 FROM ky b WHERE b.k = a.id AND b.j > a.v + 100 AND 10 /"
                + " b.z > a.v)) FROM ka a",
                "6"},
            {"SELECT COUNT_IF(w IN (SELECT b.j FROM ky b WHERE b.k = a.id AND b.j > a.v + 100 AND 10 / b.z"
                + " > a.v)) FROM ka a",
                "0"},
            {"SELECT COUNT((SELECT MAX(b.j) FROM ky b WHERE b.k = a.id AND 10 / b.z > a.v AND b.j > a.v + "
                + "100)) FROM ka a",
                "0"},
            {"SELECT COUNT_IF(w IN (SELECT 10 / b.z FROM ky b WHERE b.k = a.id AND b.j * 2 > a.v + 100)) "
                + "FROM ka a",
                "0"},
            {"SELECT COUNT_IF(w IN (SELECT 10 / b.z FROM ky b WHERE b.k = a.id AND a.v + 100 < b.j)) FROM "
                + "ka a",
                "0"},
            {"SELECT COUNT_IF(w IN (SELECT 10 / b.z FROM ky b WHERE b.k = a.id AND b.j + a.v > 100)) FROM "
                + "ka a",
                "0"},
            {"SELECT COUNT_IF(w IN (SELECT 10 / b.z FROM ky5 b WHERE b.k = a.id AND b.j > a.v + 30)) FROM "
                + "ka a",
                "REFUSED Division by zero"},
            {"SELECT COUNT_IF(w IN (SELECT 10 / b.z FROM ky7 b WHERE b.k = a.id AND b.j < a.v - 3)) FROM "
                + "ka a",
                "REFUSED Division by zero"},
            {"SELECT COUNT_IF(w IN (SELECT 10 / b.z FROM kp b WHERE b.k = a.id AND b.j > a.v + 3 AND b.j2 "
                + "> a.w)) FROM ka a",
                "REFUSED Division by zero"},
            {"SELECT COUNT_IF(EXISTS (SELECT 1 FROM ky b WHERE b.k = a.id AND 10 / b.z > a.v + 100)) FROM "
                + "ka a",
                "REFUSED Division by zero"},
            {"SELECT COUNT_IF(w IN (SELECT 10 / b.z FROM ky b WHERE b.k = a.id AND b.t > a.s || 'zz')) "
                + "FROM ka a",
                "REFUSED Division by zero"},
        });
    }
}
