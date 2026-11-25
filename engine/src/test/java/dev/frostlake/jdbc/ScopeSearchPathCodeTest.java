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
 * A listing's scope name that names no database first, as IN SCHEMA, IN CONNECTION and IN ORGANIZATION with one
 * part write it, is refused with the account driver's code and state 1059 / 22023 (live-verified).
 */
public class ScopeSearchPathCodeTest extends BaseJdbcTest {

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
    public void aScopeOfOnePartNamesNoDatabase() {
        final String sentence = "1059|22023|SQL compilation error:\n"
            + "Must specify the full search path starting from database for SP_NOSUCH";
        assertEquals(sentence, refusal("SHOW TABLES IN CONNECTION sp_nosuch"));
        assertEquals(sentence, refusal("SHOW VIEWS IN ORGANIZATION sp_nosuch"));
        assertEquals(sentence, refusal("SHOW USERS IN ORGANIZATION sp_nosuch"));
        assertEquals(sentence, refusal("SHOW IMPORTED KEYS IN SCHEMA sp_nosuch"));
        assertEquals(sentence, refusal("SHOW EXPORTED KEYS IN SCHEMA sp_nosuch"));
    }
}
