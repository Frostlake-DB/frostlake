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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A table function's argument in a subquery, under a qualifier neither the subquery's FROM nor the query around
 * it answers to: {@code FLATTEN(INPUT => zz.arr)} is no read of the outer {@code arr}. The statement is refused
 * as it compiles, in every clause and over a WHERE that reads no row, and so is the relation's own name where
 * the statement gave it an alias. Every cell is live-verified.
 */
public class TableFunctionArgumentQualifierTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE t (id VARCHAR, arr ARRAY, n INT)");
        engine.execute("""
            INSERT INTO t
            SELECT 'p1', ARRAY_CONSTRUCT(1,2,3), 2
            UNION ALL SELECT 'p2', ARRAY_CONSTRUCT(3), 3
            UNION ALL SELECT 'p3', ARRAY_CONSTRUCT(), NULL
            """);
        engine.execute("CREATE OR REPLACE TABLE u (k INT, v VARCHAR)");
        engine.execute("INSERT INTO u VALUES (1,'a'),(2,'b'),(3,'c')");
    }

    /** Every row's cells joined on one line, "(0 rows)" for none, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder rows = new StringBuilder();
            while (rs.next()) {
                rows.append(rows.length() > 0 ? " | " : "");
                for (int c = 0; c < rs.getColumns().size(); c++) {
                    rows.append(c > 0 ? ", " : "").append(String.valueOf(rs.getValue(c)));
                }
            }
            return rows.length() > 0 ? rows.toString() : "(0 rows)";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private void verify(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    /** In a query's WHERE, HAVING, QUALIFY and ORDER BY the unknown qualifier is refused at the argument. */
    @Test
    public void anUnknownQualifierIsRefusedInEveryClause() {
        verify(new String[][] {
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => zz.arr)) f) ORDER BY 1",
                "SQL compilation error: error line 1 at position 72|invalid identifier 'ZZ.ARR'"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM u, TABLE(FLATTEN(INPUT => zz.arr)) f) ORDER BY 1",
                "SQL compilation error: error line 1 at position 75|invalid identifier 'ZZ.ARR'"},
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => zz.arr)) f) ORDER BY 1",
                "SQL compilation error: error line 1 at position 82|invalid identifier 'ZZ.ARR'"},
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT f.value FROM u, TABLE(FLATTEN(INPUT => zz.arr)) f) ORDER BY 1",
                "SQL compilation error: error line 1 at position 85|invalid identifier 'ZZ.ARR'"},
            {"SELECT id FROM t WHERE n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => zz.arr)) f) ORDER BY 1",
                "SQL compilation error: error line 1 at position 76|invalid identifier 'ZZ.ARR'"},
            {"SELECT id FROM t WHERE n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => t.arr)) f) ORDER BY 1",
                "p3"},
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => t.arr)) f) ORDER BY 1",
                "SQL compilation error: error line 1 at position 82|invalid identifier 'T.ARR'"},
            {"SELECT x.id FROM t x JOIN u ON u.k = x.n WHERE x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => zz.arr)) f) ORDER BY 1",
                "SQL compilation error: error line 1 at position 102|invalid identifier 'ZZ.ARR'"},
            {"SELECT x.id FROM t x WHERE (SELECT MAX(f.value) FROM TABLE(FLATTEN(INPUT => zz.arr)) f) > 0 ORDER BY 1",
                "SQL compilation error: error line 1 at position 76|invalid identifier 'ZZ.ARR'"},
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => zz.arr)) f) AND x.n > 0 ORDER BY 1",
                "SQL compilation error: error line 1 at position 82|invalid identifier 'ZZ.ARR'"},
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) ORDER BY 1",
                "p3"},
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => arr)) f) ORDER BY 1",
                "p3"},
            {"SELECT x.id FROM t x GROUP BY x.id, x.n HAVING x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => zz.arr)) f) ORDER BY 1",
                "SQL compilation error: error line 1 at position 102|invalid identifier 'ZZ.ARR'"},
            {"SELECT x.id FROM t x ORDER BY x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => zz.arr)) f), 1",
                "SQL compilation error: error line 1 at position 85|invalid identifier 'ZZ.ARR'"},
            {"SELECT x.id FROM t x QUALIFY ROW_NUMBER() OVER (ORDER BY x.id) > 0 AND x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => zz.arr)) f) ORDER BY 1",
                "SQL compilation error: error line 1 at position 126|invalid identifier 'ZZ.ARR'"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM TABLE(SPLIT_TO_TABLE(zz.id, 'p')) f) ORDER BY 1",
                "SQL compilation error: error line 1 at position 70|invalid identifier 'ZZ.ID'"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM u WHERE u.k = zz.n) ORDER BY 1",
                "SQL compilation error: error line 1 at position 63|invalid identifier 'ZZ.N'"},
            {"SELECT x.id FROM t x WHERE FALSE AND EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => zz.arr)) f) ORDER BY 1",
                "SQL compilation error: error line 1 at position 82|invalid identifier 'ZZ.ARR'"},
        });
    }

    /** A statement's refusal on one line, or "OK" where it runs. */
    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "OK";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** A DELETE's and an UPDATE's WHERE refuse it alike. */
    @Test
    public void anUnknownQualifierIsRefusedInADmlFilter() {
        assertEquals("SQL compilation error: error line 1 at position 77|invalid identifier 'ZZ.ARR'",
            refusal("DELETE FROM t x WHERE x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => zz.arr)) f)"));
        assertEquals("SQL compilation error: error line 1 at position 72|invalid identifier 'ZZ.ARR'",
            refusal("UPDATE t x SET n = 1 WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => zz.arr)) f)"));
        assertEquals("p1, 2 | p2, 3 | p3, null", answer("SELECT id, n FROM t ORDER BY 1"));
    }
}
