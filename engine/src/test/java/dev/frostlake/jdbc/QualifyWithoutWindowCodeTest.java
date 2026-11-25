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

package dev.frostlake.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseJdbcTest;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A QUALIFY that holds no window function is refused with code 992 and state 42601 (live-verified), whatever the
 * statement carrying it — a plain or grouped query, a grouping-sets query, a FROM-less one, a view, a CTAS or an
 * INSERT — and a block's handler reads the same SQLCODE and SQLSTATE.
 */
public class QualifyWithoutWindowCodeTest extends BaseJdbcTest {

    private static final String SENTENCE = "found QUALIFY clause but no window function.";

    @BeforeEach
    public void createTable() throws SQLException {
        statement.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        statement.execute("INSERT INTO t VALUES (1, 2), (1, 3), (2, 4)");
    }

    /** The code, the state and the sentence a statement is refused with. */
    private String refusal(final String sql) {
        final SQLException refused = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.execute(sql);
            }
        });
        return refused.getErrorCode() + "|" + refused.getSQLState() + "|" + refused.getMessage();
    }

    /** The refusal answer of a QUALIFY placed at {@code position} of line 1. */
    private static String expected(final int position) {
        return "992|42601|SQL compilation error: error line 1 at position " + position + "\n" + SENTENCE;
    }

    @Test
    public void everyStatementShapeCarriesTheCode() {
        assertEquals(expected(16), refusal("select a from t qualify a > 1"));
        assertEquals(expected(35), refusal("select a, sum(b) from t group by a qualify sum(b) > 2"));
        assertEquals(expected(56),
            refusal("select a, sum(b) from t group by grouping sets ((a),()) qualify sum(b) > 2"));
        assertEquals(expected(9), refusal("select 1 qualify true"));
        assertEquals(expected(31), refusal("select * from (select a from t qualify a > 1)"));
        assertEquals(expected(44), refusal("create or replace view v as select a from t qualify a > 1"));
        assertEquals(expected(46), refusal("create or replace table t2 as select a from t qualify a > 1"));
        assertEquals(expected(33), refusal("insert into t select a, b from t qualify a > 1"));
    }

    @Test
    public void aHandlerReadsTheCode() throws SQLException {
        final ResultSet rows = statement.executeQuery("execute immediate $$begin select a, sum(b) from t group by a "
            + "qualify sum(b) > 2; return 'ok'; exception when other then return sqlcode || '/' || sqlstate; end$$");
        rows.next();
        assertEquals("992/42601", rows.getString(1));
    }
}
