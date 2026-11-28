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

package dev.frostlake.dml;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An UPDATE whose target carries an alias: a subquery of a SET value reads the target's row by that alias, as
 * one of the WHERE does, and the table's own name names nothing once the alias replaced it. Every cell is
 * live-verified.
 */
public class UpdateTargetAliasSubqueryTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE t (id VARCHAR, n INT)");
        engine.execute("INSERT INTO t VALUES ('p1', 2), ('p2', 3), ('p3', NULL)");
        engine.execute("CREATE OR REPLACE TABLE u (k INT, v VARCHAR)");
        engine.execute("INSERT INTO u VALUES (1,'a'),(2,'b'),(3,'c')");
    }

    /** The table's rows, cells joined on one line. */
    private String rows() {
        final ResultSet rs = engine.executeQuery("SELECT id, n FROM t ORDER BY 1");
        final StringBuilder rows = new StringBuilder();
        while (rs.next()) {
            rows.append(rows.length() > 0 ? " | " : "").append(String.valueOf(rs.getValue(0))).append(", ")
                .append(String.valueOf(rs.getValue(1)));
        }
        return rows.toString();
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

    /** A SET value's subquery reads the aliased target's row. */
    @Test
    public void aSetSubqueryReadsTheTargetByItsAlias() {
        engine.execute("UPDATE t x SET n = (SELECT MAX(y.k) + 10 FROM u y WHERE y.k = x.n)");
        assertEquals("p1, 12 | p2, 13 | p3, null", rows());
        engine.execute("UPDATE t x SET x.n = (SELECT MAX(y.k) FROM u y WHERE y.k = x.n - 10)");
        assertEquals("p1, 2 | p2, 3 | p3, null", rows());
        engine.execute("UPDATE t x SET n = x.n + 1 WHERE EXISTS (SELECT 1 FROM u y WHERE y.k = x.n)");
        assertEquals("p1, 3 | p2, 4 | p3, null", rows());
    }

    /** The table's name is no qualifier beside its alias, and a subquery answering several rows is refused. */
    @Test
    public void theTablesNameAndARowSubqueryAreRefused() {
        assertEquals("SQL compilation error: error line 1 at position 57|invalid identifier 'T.N'",
            refusal("UPDATE t x SET n = (SELECT MAX(y.k) FROM u y WHERE y.k = t.n)"));
        assertEquals("SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 20",
            refusal("UPDATE t x SET n = (SELECT y.k FROM u y WHERE y.k = x.n - 1)"));
        engine.execute("UPDATE t SET n = (SELECT MAX(y.k) FROM u y WHERE y.k = t.n)");
        assertEquals("p1, 2 | p2, 3 | p3, null", rows());
    }
}
