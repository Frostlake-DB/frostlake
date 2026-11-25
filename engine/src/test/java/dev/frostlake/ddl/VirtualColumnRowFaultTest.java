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

package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A virtual column's row fault waits for a statement that reads the column: the write that stores the row succeeds,
 * a statement reading only the row's other columns answers, and whatever reads the column - a star, a filter, a sort
 * or join key, an aggregate, a copy into another table - raises the fault (live-verified).
 */
public class VirtualColumnRowFaultTest extends MeasuredCellsTestSupport {

    private static final String ZERO = "REFUSED Division by zero";

    @Test
    public void aRowFaultWaitsForTheRead() {
        engine.execute("CREATE OR REPLACE TABLE vf1 (a INT, b NUMBER(7,6) AS (1 / a), d INT)");
        engine.execute("INSERT INTO vf1 (a, d) VALUES (2, 1), (0, 2)");
        assertCells(new String[][] {
            {"SELECT a, d FROM vf1 ORDER BY d", "2|1 ;; 0|2"},
            {"SELECT COUNT(*) FROM vf1", "2"},
            {"SELECT * FROM vf1 ORDER BY d", ZERO},
            {"SELECT a, b > 0.4 FROM vf1 WHERE a = 2", "2|true"},
            {"SELECT a FROM vf1 WHERE b > 0", ZERO},
            {"SELECT IFF(a = 0, NULL, b) > 0.4 FROM vf1 ORDER BY d", "true ;; null"},
            {"CREATE TABLE vf1c AS SELECT * FROM vf1",
                "REFUSED DML operation to table TEST_DB.TEST_SCHEMA.VF1C failed on column B with error: "
                    + "Division by zero"},
            {"SELECT COUNT(b) FROM vf1", ZERO},
            {"SELECT a FROM vf1 ORDER BY b", ZERO},
            {"SELECT d FROM vf1 GROUP BY d ORDER BY d", "1 ;; 2"},
            {"SELECT a, COALESCE(NULL, b) FROM vf1 WHERE d = 2", ZERO},
        });
        engine.execute("INSERT INTO vf1 (a, d) SELECT a, d + 10 FROM vf1");
        engine.execute("UPDATE vf1 SET d = d + 100 WHERE a = 0");
        assertCells(new String[][] {
            {"SELECT a, d FROM vf1 ORDER BY d", "2|1 ;; 2|11 ;; 0|102 ;; 0|112"},
        });
        engine.execute("UPDATE vf1 SET a = 4 WHERE d = 102");
        assertCells(new String[][] {
            {"SELECT a, b > 0, d FROM vf1 ORDER BY d", ZERO},
        });
        engine.execute("DELETE FROM vf1 WHERE a = 0");
        assertCells(new String[][] {
            {"SELECT a, b < 0.3, d FROM vf1 ORDER BY d", "2|false|1 ;; 2|false|11 ;; 4|true|102"},
        });
    }

    @Test
    public void otherReadersOfAFaultedRow() {
        engine.execute("CREATE OR REPLACE TABLE vf2 (a INT, b NUMBER(7,6) AS (1 / a))");
        engine.execute("INSERT INTO vf2 (a) VALUES (0)");
        engine.execute("CREATE TABLE vf2k CLONE vf2");
        assertCells(new String[][] {
            {"SELECT a FROM vf2k", "0"},
            {"SELECT b FROM vf2k", ZERO},
            {"SELECT a FROM vf2 UNION ALL SELECT 5", "0 ;; 5"},
            {"SELECT * FROM (SELECT * FROM vf2) WHERE a = 1", ""},
            {"SELECT a FROM (SELECT * FROM vf2)", "0"},
            {"SELECT OBJECT_CONSTRUCT(*) FROM vf2", ZERO},
            {"SELECT EXISTS (SELECT b FROM vf2)", "true"},
            {"SELECT a FROM vf2 x JOIN vf2 y USING (a)", "0"},
            {"SELECT x.a FROM vf2 x JOIN vf2 y ON x.b = y.b", ZERO},
            {"CREATE TABLE vf2t (a INT, b NUMBER(20,10)) AS SELECT * FROM vf2",
                "REFUSED DML operation to table TEST_DB.TEST_SCHEMA.VF2T failed on column B with error: "
                    + "Division by zero"},
            {"SELECT MAX(b) FROM vf2", ZERO},
        });
    }

    @Test
    public void writesAndFiltersOverAFaultedRow() {
        engine.execute("CREATE OR REPLACE TABLE vh1 (a INT, b NUMBER(7,6) AS (1 / a), c VARCHAR AS (b::VARCHAR), "
            + "d INT)");
        engine.execute("INSERT INTO vh1 (a, d) VALUES (2, 1), (0, 2)");
        assertCells(new String[][] {
            {"SELECT c FROM vh1 WHERE d = 2", ZERO},
            {"DELETE FROM vh1 WHERE b > 1", ZERO},
            {"UPDATE vh1 SET d = 5 WHERE b > 1", ZERO},
            {"SELECT COUNT(*) FROM vh1", "2"},
        });
        engine.execute("UPDATE vh1 SET a = 0 WHERE d = 1");
        engine.execute("MERGE INTO vh1 t USING (SELECT 0 k) s ON t.a = s.k WHEN MATCHED THEN UPDATE SET d = d + 10");
        assertCells(new String[][] {
            {"SELECT a, d FROM vh1 ORDER BY d", "0|11 ;; 0|12"},
            {"MERGE INTO vh1 t USING (SELECT 0 k) s ON t.b = s.k WHEN MATCHED THEN UPDATE SET d = d + 10", ZERO},
        });
        engine.execute("CREATE OR REPLACE TABLE vh2 (a INT, b NUMBER(7,6) AS (1 / a))");
        engine.execute("CREATE OR REPLACE STREAM vh2s ON TABLE vh2");
        engine.execute("INSERT INTO vh2 (a) VALUES (0), (4)");
        assertCells(new String[][] {
            {"SELECT a, METADATA$ACTION FROM vh2s ORDER BY a", "0|INSERT ;; 4|INSERT"},
            {"SELECT a, b FROM vh2s ORDER BY a", ZERO},
            {"SELECT a FROM vh2 WHERE a > 0 AND b > 0", "4"},
            {"SELECT a FROM vh2 WHERE a > 0 OR b > 0 ORDER BY a", ZERO},
            {"SELECT a, b IS NULL FROM vh2 ORDER BY a", ZERO},
            {"SELECT DISTINCT a FROM vh2 ORDER BY a", "0 ;; 4"},
            {"SELECT a FROM vh2 QUALIFY ROW_NUMBER() OVER (ORDER BY a) = 1", "0"},
            {"SELECT a, ROW_NUMBER() OVER (ORDER BY b) FROM vh2", ZERO},
            {"SELECT * FROM vh2 LIMIT 0", ""},
            {"SELECT * EXCLUDE b FROM vh2 ORDER BY a", "0 ;; 4"},
            {"SELECT HASH(*) IS NULL FROM vh2 WHERE a = 0", ZERO},
        });
    }
}
