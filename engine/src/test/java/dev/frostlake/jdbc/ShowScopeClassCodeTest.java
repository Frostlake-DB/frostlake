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
 * A SHOW listing's scope refused at its class name, a word that names no kind of scope, is a bare syntax-error line
 * with the code and state 1002 / 42601, in a block and outside one, through EXECUTE IMMEDIATE too (live-verified).
 */
public class ShowScopeClassCodeTest extends BaseJdbcTest {

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
    public void aScopeClassNameIsRefusedWithItsOwnCode() {
        assertEquals("1002|42601|syntax error line 1 at position 15 unexpected 'nosuch'.",
            refusal("SHOW TABLES IN nosuch x"));
        assertEquals("1002|42601|syntax error line 1 at position 16 unexpected 'nosuch'.",
            refusal("SHOW SCHEMAS IN nosuch x"));
        assertEquals("1002|42601|syntax error line 1 at position 15 unexpected 'nosuch'.",
            refusal("SHOW STAGES IN nosuch x"));
        assertEquals("1002|42601|syntax error line 1 at position 15 unexpected 'nosuch'.",
            refusal("SHOW TABLES IN nosuch IDENTIFIER('a')"));
        assertEquals("1002|42601|syntax error line 1 at position 15 unexpected 'nosuch'.",
            refusal("SHOW TABLES IN nosuch LIMIT"));
        assertEquals("1002|42601|syntax error line 1 at position 15 unexpected 'nosuch'.",
            refusal("SHOW TABLES IN nosuch x LIMIT 1"));
        assertEquals("1002|42601|syntax error line 1 at position 15 unexpected 'nosuch'.",
            refusal("EXECUTE IMMEDIATE 'SHOW TABLES IN nosuch x'"));
    }

    @Test
    public void inABlockToo() {
        assertEquals("1002|42601|syntax error line 1 at position 21 unexpected 'nosuch'.",
            refusal("BEGIN SHOW TABLES IN nosuch LIMIT; END;"));
        assertEquals("1002|42601|syntax error line 1 at position 21 unexpected 'nosuch'.",
            refusal("BEGIN SHOW TABLES IN nosuch STARTS; END;"));
        assertEquals("1002|42601|syntax error line 1 at position 21 unexpected 'nosuch'.",
            refusal("BEGIN SHOW TABLES IN nosuch x; END;"));
    }
}
