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
 * A window call inside a subquery names the subquery's own relations: {@code (SELECT SUM(y.n) OVER () FROM t y)}
 * reads {@code y}, as a value, under IN, NOT IN, ANY and EXISTS, and a qualifier or a column its FROM does not
 * hold is refused where it is written. Every cell is live-verified.
 */
public class SubqueryWindowArgumentScopeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE t (id VARCHAR, arr ARRAY, n INT)");
        engine.execute("INSERT INTO t SELECT 'p1', ARRAY_CONSTRUCT(1,2,3), 2 UNION ALL SELECT 'p2', ARRAY_CONSTRUCT(3), 3");
    }

    /** Every row's cells joined as the probe prints them, "(0 rows)" for none, or the refusal on one line. */
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

    /** A window's argument under the subquery's own alias or relation name is read. */
    @Test
    public void aWindowArgumentQualifiedByTheSubquerysRelationIsRead() {
        final String[][] cells = {
            {"SELECT SUM(y.n) OVER () FROM t y",
                "5 | 5"},
            {"SELECT id, (SELECT SUM(y.n) OVER () FROM t y LIMIT 1) FROM t x ORDER BY 1",
                "p1, 5 | p2, 5"},
            {"SELECT id FROM t x WHERE n IN (SELECT SUM(y.n) OVER () FROM t y) ORDER BY 1",
                "(0 rows)"},
            {"SELECT id, n IN (SELECT SUM(t.n) OVER () FROM t) FROM t x ORDER BY 1",
                "p1, false | p2, false"},
            {"SELECT id, EXISTS (SELECT SUM(y.n) OVER () FROM t y) FROM t x ORDER BY 1",
                "p1, true | p2, true"},
            {"SELECT id, n IN (SELECT SUM(y.n) OVER (PARTITION BY y.id) FROM t y) FROM t x ORDER BY 1",
                "p1, true | p2, true"},
            {"SELECT id, n IN (SELECT SUM(n) OVER (PARTITION BY y.id ORDER BY y.n) FROM t y) FROM t x ORDER BY 1",
                "p1, true | p2, true"},
            {"SELECT id, n IN (SELECT y.n FROM t y) FROM t x ORDER BY 1",
                "p1, true | p2, true"},
            {"SELECT id, n IN (SELECT SUM(y.n) OVER () FROM t y) FROM t ORDER BY 1",
                "p1, false | p2, false"},
            {"SELECT id, n = ANY (SELECT SUM(y.n) OVER () FROM t y) FROM t x ORDER BY 1",
                "p1, false | p2, false"},
            {"SELECT id, n IN (SELECT LAG(y.n) OVER (ORDER BY y.id) FROM t y) FROM t x ORDER BY 1",
                "p1, true | p2, null"},
            {"SELECT id FROM t x WHERE n NOT IN (SELECT SUM(y.n) OVER () FROM t y WHERE y.id = x.id) ORDER BY 1",
                "(0 rows)"},
            {"SELECT id, n IN (SELECT SUM(y.n) OVER () FROM t y JOIN t z ON z.id = y.id) FROM t x ORDER BY 1",
                "p1, false | p2, false"},
            {"SELECT id, n IN (SELECT SUM(q.n) OVER () FROM (SELECT n FROM t) q) FROM t x ORDER BY 1",
                "p1, false | p2, false"},
            {"SELECT id FROM t x WHERE EXISTS (SELECT RANK() OVER (ORDER BY y.n) FROM t y WHERE y.n > 100) ORDER BY 1",
                "(0 rows)"},
            {"SELECT id, n IN (SELECT SUM(y.n) OVER () + COUNT(y.id) OVER () FROM t y) FROM t x ORDER BY 1",
                "p1, false | p2, false"},
            {"SELECT id, n IN (SELECT SUM(z.n) OVER () FROM t y JOIN t z ON z.id = y.id) FROM t x ORDER BY 1",
                "p1, false | p2, false"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    /** A qualifier or a column the subquery's FROM does not hold, a correlation inside the window and the shapes the plan cannot evaluate are refused. */
    @Test
    public void aNameTheSubquerysFromDoesNotHoldIsRefused() {
        final String[][] cells = {
            {"SELECT id, n IN (SELECT SUM(y.n + x.n) OVER () FROM t y) FROM t x ORDER BY 1",
                "SQL compilation error:|Window function [SUM(Y.N + X.N) OVER ()] contains a correlation."},
            {"SELECT id, n IN (SELECT SUM(zz.n) OVER () FROM t y) FROM t x ORDER BY 1",
                "SQL compilation error: error line 1 at position 28|invalid identifier 'ZZ.N'"},
            {"SELECT id, n IN (SELECT SUM(y.nosuch) OVER () FROM t y) FROM t x ORDER BY 1",
                "SQL compilation error: error line 1 at position 28|invalid identifier 'Y.NOSUCH'"},
            {"SELECT id, (SELECT SUM(zz.n) OVER ()) FROM t x ORDER BY 1",
                "SQL compilation error: error line 1 at position 23|invalid identifier 'ZZ.N'"},
            {"SELECT id, n IN (SELECT SUM(t.n) OVER () FROM t y) FROM t x ORDER BY 1",
                "SQL compilation error: error line 1 at position 28|invalid identifier 'T.N'"},
            {"SELECT id, n IN (SELECT SUM(x.n) OVER () FROM t y) FROM t x ORDER BY 1",
                "SQL compilation error:|Window function [SUM(X.N) OVER ()] contains a correlation."},
            {"SELECT id, (SELECT MAX(y.n) OVER (ORDER BY y.id) FROM t y WHERE y.id = x.id) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 12"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }
}
