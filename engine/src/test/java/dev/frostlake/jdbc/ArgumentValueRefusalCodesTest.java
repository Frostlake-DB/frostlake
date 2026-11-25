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
 * The refusals of a function's argument for its value carry the account driver's code and state (live-verified), all
 * 22023: an argument a function takes only as a constant 1015, a constant it does not take 1046 and a value a running
 * call does not take 100050.
 */
public class ArgumentValueRefusalCodesTest extends BaseJdbcTest {

    private static final String ROWS = " FROM (SELECT 1 g, 1 o, 'a' x)";

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
    public void anArgumentTakenOnlyAsAConstant() {
        assertEquals("1015|22023|SQL compilation error:\nargument 3 to function MAX_BY needs to be constant, found '1 + 1'",
            refusal("SELECT MAX_BY(x, o, 1 + 1)" + ROWS));
        assertEquals("1015|22023|SQL compilation error:\nargument 3 to function MIN_BY needs to be constant, found "
            + "'CAST(2.5 AS NUMBER(18,0))'", refusal("SELECT MIN_BY(x, o, 2.5)" + ROWS));
        assertEquals("1015|22023|SQL compilation error:\nargument 0 to function SYSTEM$GET_TAG needs to be constant, found "
            + "'null'", refusal("SELECT SYSTEM$GET_TAG(NULL, 't', 'table')"));
    }

    @Test
    public void aConstantAFunctionDoesNotTake() {
        assertEquals("1046|22023|SQL compilation error:\nInvalid value [0] for function 'MAX_BY', parameter 3: N (last "
            + "argument) must be a positive number", refusal("SELECT MAX_BY(x, o, 0)" + ROWS));
        assertEquals("1046|22023|SQL compilation error:\nInvalid value [1001] for function 'MIN_BY', parameter 3: N "
            + "(last argument) cannot be larger than 1000", refusal("SELECT MIN_BY(x, o, 1001) OVER ()" + ROWS));
        assertEquals("1046|22023|SQL compilation error:\nInvalid value [0] for function 'APPROX_TOP_K', parameter 1: "
            + "Number of items must be a positive integer", refusal("SELECT APPROX_TOP_K(x, 0)" + ROWS));
    }

    @Test
    public void aValueARunningCallDoesNotTake() {
        assertEquals("100050|22023|Invalid parameter value: 0. Reason: NTILE argument must be at least 1",
            refusal("SELECT NTILE(0) OVER (ORDER BY o)" + ROWS));
        assertEquals("100050|22023|Invalid parameter value: 100. Reason: invalid SHA2 digest size",
            refusal("SELECT SHA2('a', 100)"));
    }
}
