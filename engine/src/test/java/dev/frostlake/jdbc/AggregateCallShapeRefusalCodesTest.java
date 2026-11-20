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
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The refusals of a call's shape carry the account driver's code and state (live-verified): a cumulative window frame
 * a function does not take is 2303 / 0A000, a WITHIN GROUP a function does not take 2070 / 42601.
 */
public class AggregateCallShapeRefusalCodesTest extends BaseJdbcTest {

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

    @Test
    public void aCumulativeFrameAFunctionDoesNotTake() {
        assertEquals("2303|0A000|SQL compilation error: error line 1 at position 21\nCumulative window frame unsupported "
            + "for function BITAND_AGG", refusal("SELECT BITAND_AGG(x) OVER (ORDER BY x) FROM (SELECT 1 x)"));
        assertEquals("2303|0A000|SQL compilation error: error line 1 at position 17\nCumulative window frame unsupported "
            + "for function MEDIAN", refusal("SELECT MEDIAN(x) OVER (ORDER BY x) FROM (SELECT 1 x)"));
    }

    @Test
    public void aWithinGroupAFunctionDoesNotTake() {
        assertEquals("2070|42601|SQL compilation error:\nFunction BITOR_AGG does not support WITHIN GROUP clause.",
            refusal("SELECT BITOR_AGG(x) WITHIN GROUP (ORDER BY x) FROM (SELECT 1 x)"));
        assertEquals("2070|42601|SQL compilation error:\nFunction SUM does not support WITHIN GROUP clause.",
            refusal("SELECT SUM(x) WITHIN GROUP (ORDER BY x) FROM (SELECT 1 x)"));
    }
}
